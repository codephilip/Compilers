package edu.montana.csci.csci468.parser;

import edu.montana.csci.csci468.parser.expressions.*;
import edu.montana.csci.csci468.parser.statements.*;
import edu.montana.csci.csci468.tokenizer.CatScriptTokenizer;
import edu.montana.csci.csci468.tokenizer.Token;
import edu.montana.csci.csci468.tokenizer.TokenList;
import edu.montana.csci.csci468.tokenizer.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static edu.montana.csci.csci468.tokenizer.TokenType.*;

public class CatScriptParser {

    private TokenList tokens;
    private FunctionDefinitionStatement currentFunctionDefinition;

    public CatScriptProgram parse(String source) {
        tokens = new CatScriptTokenizer(source).getTokens();

        // first parse an expression
        CatScriptProgram program = new CatScriptProgram();
        program.setStart(tokens.getCurrentToken());
        Expression expression = null;
        try {
            expression = parseExpression();
        } catch (RuntimeException re) {
            // ignore :)
        }
        if (expression == null || tokens.hasMoreTokens()) {
            tokens.reset();
            while (tokens.hasMoreTokens()) {
                program.addStatement(parseProgramStatement());
            }
        } else {
            program.setExpression(expression);
        }

        program.setEnd(tokens.getCurrentToken());
        return program;
    }

    public CatScriptProgram parseAsExpression(String source) {
        tokens = new CatScriptTokenizer(source).getTokens();
        CatScriptProgram program = new CatScriptProgram();
        program.setStart(tokens.getCurrentToken());
        Expression expression = parseExpression();
        program.setExpression(expression);
        program.setEnd(tokens.getCurrentToken());
        return program;
    }

    // ============================================================
    // Statements
    // ============================================================

    private Statement parseProgramStatement() {
        Statement stmt = parseStatement();
        if (stmt != null) {
            return stmt;
        } else if (currentFunctionDefinition != null) {
            return parseReturnStatement();
        }
        return new SyntaxErrorStatement(tokens.consumeToken());
    }

    private Statement parseStatement() {

        if (tokens.match(VAR)) {
            return parseVarStatement();
        } else if (tokens.match(FUNCTION)) {
            return parseFunctionDeclaration();
        } else if (tokens.match(IF)) {
            return parseIfStatement();
        } else if (tokens.match(FOR)) {
            return parseForStatement();
        } else if (tokens.match(PRINT)) {
            return parsePrintStatement();
        } else if (tokens.match(IDENTIFIER)) {
            Token t = tokens.getCurrentToken();
            tokens.consumeToken();
            if (tokens.matchAndConsume(EQUAL)) {
                return parseAssignmentStatement(t);
            }
            return new FunctionCallStatement(parseFunctionCallExpression(t.getStringValue()));
        }
        return null;
    }

    private Statement parseVarStatement() {
        VariableStatement variableStatement = new VariableStatement();
        variableStatement.setStart(tokens.consumeToken());

        variableStatement.setVariableName(require(IDENTIFIER, variableStatement).getStringValue());
        if (tokens.matchAndConsume(COLON)) {
            CatscriptType type;
            type = parseTypeExpression();
            variableStatement.setExplicitType(type);
        }
        require(EQUAL, variableStatement);
        variableStatement.setExpression(parseExpression());
        variableStatement.setEnd(tokens.lastToken());
        return variableStatement;
    }

    private Statement parseFunctionDeclaration() {
        if (tokens.matchAndConsume(FUNCTION)) {
            FunctionDefinitionStatement functionDefinitionStatement = new FunctionDefinitionStatement();
            functionDefinitionStatement.setStart(tokens.lastToken());
            functionDefinitionStatement.setName(require(IDENTIFIER, functionDefinitionStatement).getStringValue());
            
            require(LEFT_PAREN, functionDefinitionStatement);
            HashMap<String, TypeLiteral> parameters = parseParameterList();
            for (String parameterName : parameters.keySet()) {
                functionDefinitionStatement.addParameter(parameterName, parameters.get(parameterName));
            }
            
            require(RIGHT_PAREN, functionDefinitionStatement);
            TypeLiteral functionType = new TypeLiteral();
            if (tokens.matchAndConsume(COLON)) {
                functionType.setType(parseTypeExpression());
            } else {
                functionType.setType(CatscriptType.VOID);
            }
            functionDefinitionStatement.setType(functionType);
            
            require(LEFT_BRACE, functionDefinitionStatement);
            currentFunctionDefinition = functionDefinitionStatement;
            List<Statement> bodyList = new ArrayList<>();
            try {
                while (!tokens.match(EOF) && !tokens.match(RIGHT_BRACE)) {
                    bodyList.add(parseProgramStatement());
                }
            } finally {
                currentFunctionDefinition = null;
            }
            functionDefinitionStatement.setBody(bodyList);
            functionDefinitionStatement.setEnd(require(RIGHT_BRACE, functionDefinitionStatement));
            return functionDefinitionStatement;
        } else {
            return null;
        }
    }

    private Statement parseIfStatement() {
        IfStatement ifStatement = new IfStatement();
        ifStatement.setStart(tokens.consumeToken());

        require(LEFT_PAREN, ifStatement);
        ifStatement.setExpression(parseExpression());
        require(RIGHT_PAREN, ifStatement);
        require(LEFT_BRACE, ifStatement);
        List<Statement> statementList = new ArrayList<>();
        while (!tokens.match(ELSE) && !tokens.match(EOF) && !tokens.match(RIGHT_BRACE)) {
            statementList.add(parseProgramStatement());
        }
        ifStatement.setTrueStatements(statementList);
        ifStatement.setEnd(require(RIGHT_BRACE, ifStatement));
        if (tokens.match(ELSE)) {
            tokens.consumeToken();
            List<Statement> elseStatements = new ArrayList<>();
            if (tokens.match(IF)) {
                elseStatements.add(parseIfStatement());
            } else {
                require(LEFT_BRACE, ifStatement);
                while (!tokens.match(EOF) && !tokens.match(RIGHT_BRACE)) {
                    elseStatements.add(parseProgramStatement());
                }
                require(RIGHT_BRACE, ifStatement);
            }
            ifStatement.setElseStatements(elseStatements);
        }
        return ifStatement;
    }

    private Statement parseForStatement() {
        ForStatement forStatement = new ForStatement();
        forStatement.setStart(tokens.consumeToken());

        require(LEFT_PAREN, forStatement);
        forStatement.setVariableName(require(IDENTIFIER, forStatement).getStringValue());
        require(IN, forStatement);
        forStatement.setExpression(parseExpression());
        require(RIGHT_PAREN, forStatement);
        require(LEFT_BRACE, forStatement);
        List<Statement> statementList = new ArrayList<>();
        while (!tokens.match(RIGHT_BRACE) && !tokens.match(EOF)) {
            statementList.add(parseStatement());
        }
        forStatement.setBody(statementList);
        forStatement.setEnd(require(RIGHT_BRACE, forStatement));
        return forStatement;
    }

    private Statement parsePrintStatement() {
        PrintStatement printStatement = new PrintStatement();
        printStatement.setStart(tokens.consumeToken());
        require(LEFT_PAREN, printStatement);
        printStatement.setExpression(parseExpression());
        printStatement.setEnd(require(RIGHT_PAREN, printStatement));

        return printStatement;
    }

    private Statement parseAssignmentStatement(Token identifier) {
        AssignmentStatement assignmentStatement = new AssignmentStatement();
        assignmentStatement.setStart(identifier);
        assignmentStatement.setVariableName(identifier.getStringValue());
        assignmentStatement.setExpression(parseExpression());
        assignmentStatement.setEnd(tokens.lastToken());
        return assignmentStatement;
    }

    private HashMap<String, TypeLiteral> parseParameterList() {
        HashMap<String, TypeLiteral> parameters = new HashMap<>();
        TypeLiteral parameter = new TypeLiteral();
        if (!tokens.match(RIGHT_PAREN)) {
            do {
                String parameterName = tokens.getCurrentToken().getStringValue();
                tokens.consumeToken();
                if (tokens.matchAndConsume(COLON)) {
                    parameter.setType(parseTypeExpression());
                } else {
                    parameter.setType(CatscriptType.OBJECT);
                }
                parameters.put(parameterName, parameter);
                parameter = new TypeLiteral();
            } while (tokens.matchAndConsume(COMMA));
        }
        return parameters;
    }

    private Statement parseReturnStatement() {
        ReturnStatement returnStatement = new ReturnStatement();
        returnStatement.setStart(tokens.consumeToken());
        returnStatement.setFunctionDefinition(currentFunctionDefinition);
        if (!tokens.match(EOF) && !tokens.match(RIGHT_BRACE)) {
            returnStatement.setExpression(parseExpression());
        }
        return returnStatement;
    }

    // ============================================================
    // Expressions
    // ============================================================

    private Expression parseExpression() {
        return parseEqualityExpression();
    }

    private Expression parseEqualityExpression() {
        Expression expression = parseComparisonExpression();
        if (tokens.match(EQUAL_EQUAL, BANG_EQUAL)) {
            Token operator = tokens.consumeToken();
            Expression rhs = parseEqualityExpression();
            return new EqualityExpression(operator, expression, rhs);
        } else {
            return expression;
        }
    }

    private Expression parseComparisonExpression() {
        Expression expression = parseAdditiveExpression();
        while (tokens.match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = tokens.consumeToken();
            final Expression leftHandSide = expression;
            final Expression rightHandSide = parseAdditiveExpression();
            expression = new ComparisonExpression(operator, leftHandSide, rightHandSide);
            expression.setStart(leftHandSide.getStart());
            expression.setEnd(rightHandSide.getEnd());
        }
        return expression;
    }

    private Expression parseAdditiveExpression() {
        Expression expression = parseFactorExpression();
        while (tokens.match(PLUS, MINUS)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseFactorExpression();
            final Expression leftHandSide = expression;
            expression = new AdditiveExpression(operator, leftHandSide, rightHandSide);
            expression.setStart(leftHandSide.getStart());
            expression.setEnd(rightHandSide.getEnd());
        }
        return expression;
    }

    private Expression parseFactorExpression() {
        Expression expression = parseUnaryExpression();
        while (tokens.match(SLASH, STAR)) {
            Token operator = tokens.consumeToken();
            final Expression leftHandSide = expression;
            final Expression rightHandSide = parseUnaryExpression();
            expression = new FactorExpression(operator, leftHandSide, rightHandSide);
            expression.setStart(leftHandSide.getStart());
            expression.setEnd(rightHandSide.getEnd());
        }
        return expression;
    }

    private Expression parseUnaryExpression() {
        if (tokens.match(MINUS, NOT)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseUnaryExpression();
            UnaryExpression unaryExpression = new UnaryExpression(operator, rightHandSide);
            unaryExpression.setStart(operator);
            unaryExpression.setEnd(rightHandSide.getEnd());
            return unaryExpression;
        } else {
            return parsePrimaryExpression();
        }
    }

    private Expression parsePrimaryExpression() {
        if (tokens.match(IDENTIFIER)) {
            Token identifierToken = tokens.consumeToken();
            if (tokens.match(LEFT_PAREN)) {
                return parseFunctionCallExpression(identifierToken.getStringValue());
            }
            IdentifierExpression identifierExpression = new IdentifierExpression(identifierToken.getStringValue());
            identifierExpression.setToken(identifierToken);
            return identifierExpression;
        } else if (tokens.match(INTEGER)) {
            Token integerToken = tokens.consumeToken();
            IntegerLiteralExpression integerExpression = new IntegerLiteralExpression(integerToken.getStringValue());
            integerExpression.setToken(integerToken);
            return integerExpression;
        } else if (tokens.match(STRING)) {
            Token stringToken = tokens.consumeToken();
            StringLiteralExpression stringLiteralExpression = new StringLiteralExpression(stringToken.getStringValue());
            stringLiteralExpression.setToken(stringToken);
            return stringLiteralExpression;
        } else if (tokens.match(TRUE, FALSE)) {
            Token booleanToken = tokens.consumeToken();
            BooleanLiteralExpression booleanExpression = new BooleanLiteralExpression(
                    Boolean.parseBoolean(booleanToken.getStringValue()));
            booleanExpression.setToken(booleanToken);
            return booleanExpression;
        } else if (tokens.match(NULL)) {
            Token nullToken = tokens.consumeToken();
            NullLiteralExpression nullExpression = new NullLiteralExpression();
            nullExpression.setToken(nullToken);
            return nullExpression;
        } else if (tokens.match(LEFT_BRACKET)) {
            return parseListLiteralExpression();
        } else if (tokens.match(LEFT_PAREN)) {
            return parseParenthesizedExpression();
        } else {
            SyntaxErrorExpression syntaxErrorExpression = new SyntaxErrorExpression(tokens.consumeToken());
            return syntaxErrorExpression;
        }
    }

    ParenthesizedExpression parseParenthesizedExpression() {
        tokens.consumeToken();
        Expression expression = parseExpression();
        tokens.consumeToken();
        return new ParenthesizedExpression(expression);
    }

    ListLiteralExpression parseListLiteralExpression() {
        tokens.consumeToken();
        List<Expression> expressionList = new ArrayList<>();
        if (!tokens.match(RIGHT_BRACKET)) {
            do {
                expressionList.add(parseExpression());
            } while (tokens.matchAndConsume(COMMA));
        }
        ListLiteralExpression listLiteralExpression = new ListLiteralExpression(expressionList);
        if (!tokens.match(RIGHT_BRACKET)) {
            listLiteralExpression.addError(ErrorType.UNTERMINATED_LIST);
        } else {
            tokens.consumeToken();
        }
        return listLiteralExpression;
    }

    FunctionCallExpression parseFunctionCallExpression(String functionName) {
        tokens.consumeToken();
        List<Expression> argumentList = new ArrayList<>();
        if (tokens.matchAndConsume(RIGHT_PAREN)) {
            return new FunctionCallExpression(functionName, argumentList);
        }
        do {
            argumentList.add(parseExpression());
        } while (tokens.matchAndConsume(COMMA));
        FunctionCallExpression functionCallExpression = new FunctionCallExpression(functionName, argumentList);
        if (!tokens.match(RIGHT_PAREN)) {
            functionCallExpression.addError(ErrorType.UNTERMINATED_ARG_LIST);
        } else {
            tokens.consumeToken();
        }
        return functionCallExpression;
    }

    private CatscriptType parseTypeExpression() {
        CatscriptType type = null;
        String typeLiteral = tokens.getCurrentToken().getStringValue();
        tokens.consumeToken();
        switch (typeLiteral) {
            case "bool":
                type = CatscriptType.BOOLEAN;
                break;
            case "int":
                type = CatscriptType.INT;
                break;
            case "string":
                type = CatscriptType.STRING;
                break;
            case "object":
                type = CatscriptType.OBJECT;
                break;
            case "list":
                if (tokens.match(LESS)) {
                    tokens.consumeToken();
                    type = CatscriptType.getListType(parseTypeExpression());
                    tokens.consumeToken();
                } else {
                    type = CatscriptType.getListType(CatscriptType.OBJECT);
                }
                break;
        }
        return type;
    }

    // ============================================================
    // Parse Helpers
    // ============================================================
    private Token require(TokenType type, ParseElement elt) {
        return require(type, elt, ErrorType.UNEXPECTED_TOKEN);
    }

    private Token require(TokenType type, ParseElement elt, ErrorType msg) {
        if (tokens.match(type)) {
            return tokens.consumeToken();
        } else {
            elt.addError(msg, tokens.getCurrentToken());
            return tokens.getCurrentToken();
        }
    }
}