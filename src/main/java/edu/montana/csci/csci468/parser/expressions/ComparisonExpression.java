package edu.montana.csci.csci468.parser.expressions;

import edu.montana.csci.csci468.bytecode.ByteCodeGenerator;
import edu.montana.csci.csci468.eval.CatscriptRuntime;
import edu.montana.csci.csci468.parser.CatscriptType;
import edu.montana.csci.csci468.parser.ErrorType;
import edu.montana.csci.csci468.parser.ParseError;
import edu.montana.csci.csci468.parser.SymbolTable;
import edu.montana.csci.csci468.tokenizer.Token;

import static edu.montana.csci.csci468.tokenizer.TokenType.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

public class ComparisonExpression extends Expression {

    private final Token operator;
    private final Expression leftHandSide;
    private final Expression rightHandSide;

    public ComparisonExpression(Token operator, Expression leftHandSide, Expression rightHandSide) {
        this.leftHandSide = addChild(leftHandSide);
        this.rightHandSide = addChild(rightHandSide);
        this.operator = operator;
    }

    public Expression getLeftHandSide() {
        return leftHandSide;
    }

    public Expression getRightHandSide() {
        return rightHandSide;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + operator.getStringValue() + "]";
    }

    public boolean isLessThan() {
        return operator.getType().equals(LESS);
    }

    public boolean isLessThanOrEqual() {
        return operator.getType().equals(LESS_EQUAL);
    }

    public boolean isGreaterThanOrEqual() {
        return operator.getType().equals(GREATER_EQUAL);
    }

    public boolean isGreater() {
        return operator.getType().equals(GREATER);
    }

    @Override
    public void validate(SymbolTable symbolTable) {
        leftHandSide.validate(symbolTable);
        rightHandSide.validate(symbolTable);
        if (!leftHandSide.getType().equals(CatscriptType.INT)) {
            leftHandSide.addError(ErrorType.INCOMPATIBLE_TYPES);
        }
        if (!rightHandSide.getType().equals(CatscriptType.INT)) {
            rightHandSide.addError(ErrorType.INCOMPATIBLE_TYPES);
        }
    }

    @Override
    public CatscriptType getType() {
        return CatscriptType.BOOLEAN;
    }

    // ==============================================================
    // Implementation
    // ==============================================================

    @Override
    public Object evaluate(CatscriptRuntime runtime) {
        Object lhsValue = leftHandSide.evaluate(runtime);
        Object rhsValue = rightHandSide.evaluate(runtime);
        if (operator.getType().equals(GREATER)) {
            return (Integer) lhsValue > (Integer) rhsValue;
        } else if (operator.getType().equals(GREATER_EQUAL)) {
            return (Integer) lhsValue >= (Integer) rhsValue;
        } else if (operator.getType().equals(LESS)) {
            return (Integer) lhsValue < (Integer) rhsValue;
        } else {
            return (Integer) lhsValue <= (Integer) rhsValue;
        }
    }

    @Override
    public void transpile(StringBuilder javascript) {
        super.transpile(javascript);
    }

    @Override
    public void compile(ByteCodeGenerator code) {
        leftHandSide.compile(code);
        rightHandSide.compile(code);
        Label trueLbl = new Label();
        Label endLbl = new Label();
        if (isGreater()) {
            code.addJumpInstruction(Opcodes.IF_ICMPGT, trueLbl);
            compileLogic(code, trueLbl, endLbl);
        } else if (isGreaterThanOrEqual()) {
            code.addJumpInstruction(Opcodes.IF_ICMPGE, trueLbl);
            compileLogic(code, trueLbl, endLbl);
        } else if (isLessThan()) {
            code.addJumpInstruction(Opcodes.IF_ICMPLT, trueLbl);
            compileLogic(code, trueLbl, endLbl);
        } else if (isLessThanOrEqual()) {
            code.addJumpInstruction(Opcodes.IF_ICMPLE, trueLbl);
            compileLogic(code, trueLbl, endLbl);
        }
    }

    private void compileLogic(ByteCodeGenerator code, Label trueLbl, Label endLbl) {
        code.addInstruction(Opcodes.ICONST_0);
        code.addJumpInstruction(Opcodes.GOTO, endLbl);
        code.addLabel(trueLbl);
        code.addInstruction(Opcodes.ICONST_1);
        code.addLabel(endLbl);
    }

}
