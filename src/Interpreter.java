package src;

import java.beans.Expression;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import src.Expr.Assign;
import src.Expr.Binary;
import src.Expr.Call;
import src.Expr.Get;
import src.Expr.Grouping;
import src.Expr.Literal;
import src.Expr.Logical;
import src.Expr.Set;
import src.Expr.Super;
import src.Expr.This;
import src.Expr.Unary;
import src.Expr.Variable;
import src.Stmt.Block;
import src.Stmt.Class;
import src.Stmt.Function;
import src.Stmt.If;
import src.Stmt.Print;
import src.Stmt.Return;
import src.Stmt.Var;
import src.Stmt.While;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void>
{
    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();


    Interpreter()
    {
        globals.define("clock", new LoxCallable() 
        {

            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) 
            {
                return (double)System.currentTimeMillis()/1000.0;
            }

            @Override
            public String toString() {return "<native fn>";}
            
        });
    }

    void interpret(List<Stmt> statements)
    {
        try
        {
            for (Stmt statement : statements)
            {
                execute(statement);
            }
        }
        catch(RuntimeError err)
        {
            Lox.runtimeError(err);
        }
    }

    private String stringify(Object object)
    {
        if (object == null) { return "NIL"; }

        if (object instanceof Double)
        {
            String text = object.toString();
            if(text.endsWith(".0"))
            {
                text = text.substring(0, text.length()  -2);
            }
            return text;
        }

        return object.toString();
    }

    private Object evaluate(Expr expr)
    {
        return expr.accept(this);
    }

    private void execute(Stmt statement)
    {
        statement.accept(this);
    }

    void resolve(Expr expr, int depth)
    {
        locals.put(expr, depth);
    }

    @Override
    public Object visitBinaryExpr(Binary expr) 
    {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left/(double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case PLUS:
                if ((left instanceof Double) && (right instanceof Double))
                {
                    return (double)left + (double)right;
                }
                if ((left instanceof String) && (right instanceof String))
                {
                    return (String)left + (String)right;
                }

                throw new RuntimeError(expr.operator, "Operands must be 2 numbers or 2 strings");

            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;

            case BANG_EQUAL:
                 return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitGroupingExpr(Grouping expr) 
    {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Literal expr) 
    {
        return expr.value;
    }

    @Override
    public Object visitUnaryExpr(Unary expr) 
    {
        Object right = evaluate(expr.right);
        
        switch (expr.operator.type) {
            case MINUS:
            checkNumberOperand(expr.operator, right);
                return -(double)right;
        
            case BANG:
                return !isTruthy(right);
        }

        return null;

    }

    private void checkNumberOperand(Token operator, Object operand)
    {
        if (operand instanceof Double) { return; }
        throw new RuntimeError(operator, "Operand must be a Number");
    }

    private void checkNumberOperands(Token operator, Object left, Object right)
    {
        if ((left instanceof Double) && (right instanceof Double)) { return; }
        throw new RuntimeError(operator, "Operands must be a Number");
    }

    private boolean isTruthy(Object object)
    {
        if (object == null) { return false; }
        if (object instanceof Boolean) { return (boolean)object; }
        return true;
    }

    private boolean isEqual(Object a, Object b)
    {
        if (a == null && b == null) { return true; }
        if (a == null) { return false; }

        return a.equals(b);
    }

    @Override
    public Void visitExpressionStmt(src.Stmt.Expression stmt) 
    {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Print stmt) 
    {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;   
    }

    @Override
    public Void visitVarStmt(Var stmt) 
    {
        Object value = null;
        if (stmt.initializer != null)
        {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }


    @Override
    public Object visitVariableExpr(Variable expr) 
    {
        //return environment.get(expr.name);
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr)
    {
        Integer distance = locals.get(expr);
        if(distance != null)
        {
            return environment.getAt(distance, name.lexeme);
        }
        else
        {
            return globals.get(name);
        }
    }



    @Override
    public Object visitAssignExpr(Assign expr) 
    {
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if (distance != null)
        {
            environment.assignAt(distance, expr.name, value);
        }
        else
        {
            globals.assign(expr.name, value);
        }

        //environment.assign(expr.name, value);
        return value;
    }

    

    @Override
    public Void visitBlockStmt(Block stmt) 
    {
        executeBlock(stmt.statments, new Environment(environment));
        return null;
    }

    void executeBlock(List<Stmt> statements, Environment environment)
    {
        Environment previous = this.environment;
        try
        {
            this.environment = environment;

            for (Stmt statement : statements)
            {
                execute(statement);
            }
        }
        finally
        {
            this.environment = previous;
        }
    }

    @Override
    public Void visitIfStmt(If stmt) 
    {
        if (isTruthy(evaluate(stmt.condition)))
        {
            execute(stmt.thenBranch);
        }
        else if (stmt.elseBranch != null)
        {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(While stmt) 
    {
        while (isTruthy(evaluate(stmt.condition)))
        {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Object visitLogicalExpr(Logical expr) 
    {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR)
        {
            if (isTruthy(left)) {return left;}
        }
        else
        {
            if (!isTruthy(left)) {return left;}
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitCallExpr(Call expr) 
    {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments)
        {
            arguments.add(evaluate(argument));
        }

        if(!(callee instanceof LoxCallable))
        {
            throw new RuntimeError(expr.paren, "Only functions and classes are callable.");
        }

        LoxCallable function = (LoxCallable)callee;

        if(arguments.size() != function.arity())
        {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments, but got " + arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Void visitFunctionStmt(Function stmt) 
    {
        LoxFunction function = new LoxFunction(stmt, environment, false);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitReturnStmt(Return stmt) 
    {
        Object value = null;
        if(stmt.value != null){value = evaluate(stmt.value);}
        throw new src.Return(value);
    }

    @Override
    public Void visitClassStmt(Class stmt) 
    {
        Object superclass = null;
        if (stmt.superclass != null)
        {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof LoxClass))
            {
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
            }
        }


        environment.define(stmt.name.lexeme, null);

        if(stmt.superclass != null)
        {
            environment = new Environment(environment);
            environment.define("super", superclass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods)
        {
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass)superclass, methods);

        if(superclass != null){environment = environment.enclosing;}

        environment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Object visitGetExpr(Get expr) 
    {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance)
        {
            return ((LoxInstance) object).get(expr.name);
        }

        throw new RuntimeError(expr.name, "Only instances have properties");
    }

    @Override
    public Object visitSetExpr(Set expr) 
    {
        Object object = evaluate(expr.object);

        if(!(object instanceof LoxInstance))
        {
            throw new RuntimeError(expr.name,"Only instances have fields.");
        }
        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);
        return value;

    }

    @Override
    public Object visitThisExpr(This expr) 
    {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitSuperExpr(Super expr) 
    {
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass)environment.getAt(distance, "super");

        LoxInstance object = (LoxInstance)environment.getAt(distance - 1, "this");

        LoxFunction method = superclass.findMethod(expr.method.lexeme);


        if(method == null)
        {
            throw new RuntimeError(expr.method, "Undefined property '"+expr.method.lexeme + "'.");
        }

        return method.bind(object);
    }
    


}
