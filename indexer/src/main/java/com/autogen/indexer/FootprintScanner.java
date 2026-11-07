package com.autogen.indexer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class FootprintScanner {

    public List<MethodFootprint> scan(Path javaFile) throws IOException {

        CompilationUnit tree = StaticJavaParser.parse(javaFile);
        String className = tree.getPrimaryTypeName().orElse("Unknown");

        // step 1: field name -> locator
        Map<String, String> fields = readFindByFields(tree);

        // all methods by name, so we can follow calls between them
        Map<String, MethodDeclaration> methodsByName = new HashMap<>();
        for (MethodDeclaration method : tree.findAll(MethodDeclaration.class)) {
            methodsByName.put(method.getNameAsString(), method);
        }

        // step 2: footprint of every public method
        List<MethodFootprint> result = new ArrayList<>();
        for (MethodDeclaration method : tree.findAll(MethodDeclaration.class)) {
            if (!method.isPublic()) {
                continue;
            }
            List<Action> actions = collectActions(method, fields, methodsByName, new HashSet<>());

            List<String> params = new ArrayList<>();
            for (com.github.javaparser.ast.body.Parameter parameter : method.getParameters()) {
                params.add(parameter.getNameAsString());
            }

            result.add(new MethodFootprint(
                    className, method.getNameAsString(), method.getType().asString(),
                    params, actions));
        }
        return result;
    }

    // ---------- step 1: @FindBy fields ----------

    private Map<String, String> readFindByFields(CompilationUnit tree) {
        Map<String, String> fields = new HashMap<>();

        for (FieldDeclaration field : tree.findAll(FieldDeclaration.class)) {
            Optional<AnnotationExpr> findBy = field.getAnnotationByName("FindBy");
            if (findBy.isEmpty()) {
                continue;
            }
            String locator = locatorOf(findBy.get());
            if (locator == null) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                fields.put(variable.getNameAsString(), locator);
            }
        }
        return fields;
    }

    private String locatorOf(AnnotationExpr annotation) {
        if (!(annotation instanceof NormalAnnotationExpr)) {
            return null;
        }
        String how = null;
        String using = null;

        for (MemberValuePair pair : ((NormalAnnotationExpr) annotation).getPairs()) {
            String key = pair.getNameAsString();
            Expression value = pair.getValue();

            if (key.equals("how")) {
                String text = value.toString();
                how = text.substring(text.lastIndexOf('.') + 1);
            } else if (value.isStringLiteralExpr()) {
                String text = value.asStringLiteralExpr().getValue();
                if (key.equals("using")) {
                    using = text;
                } else if (isLocatorKind(key)) {
                    return normalise(key, text);
                }
            }
        }
        if (how != null && using != null) {
            return normalise(how.toLowerCase(), using);
        }
        return null;
    }

    private boolean isLocatorKind(String key) {
        return key.equals("css") || key.equals("id") || key.equals("name") || key.equals("xpath");
    }

    private String normalise(String kind, String value) {
        String clean = value.trim();
        if (kind.equals("css")) {
            clean = clean.replace('"', '\'');
        }
        return kind + ":" + clean;
    }

    // ---------- step 2: method bodies ----------

    private List<Action> collectActions(MethodDeclaration method,
                                        Map<String, String> fields,
                                        Map<String, MethodDeclaration> methodsByName,
                                        Set<String> visiting) {
        List<Action> actions = new ArrayList<>();

        if (!visiting.add(method.getNameAsString())) {
            return actions;
        }

        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            String callName = call.getNameAsString();

            // case 1:  errorMessage.getText()
            if (call.getScope().isPresent() && call.getScope().get().isNameExpr()) {
                String scopeName = call.getScope().get().asNameExpr().getNameAsString();
                if (fields.containsKey(scopeName)) {
                    actions.add(new Action(callName, fields.get(scopeName)));
                    continue;
                }
            }

            // case 1b: new Select(sortDropdown).selectByVisibleText(x)
            if (call.getScope().isPresent() && call.getScope().get().isObjectCreationExpr()) {
                boolean wrapped = false;
                for (Expression argument : call.getScope().get().asObjectCreationExpr().getArguments()) {
                    if (argument.isNameExpr()
                            && fields.containsKey(argument.asNameExpr().getNameAsString())) {
                        actions.add(new Action(callName,
                                fields.get(argument.asNameExpr().getNameAsString())));
                        wrapped = true;
                    }
                }
                if (wrapped) {
                    continue;
                }
            }

            // case 1c: By.cssSelector("..." + id + "...")  -> built while running: "dynamic"
            if (call.getScope().isPresent() && call.getScope().get().isNameExpr()
                    && call.getScope().get().asNameExpr().getNameAsString().equals("By")) {
                actions.add(new Action("dynamic", "by:" + call));
                continue;
            }

            // case 2:  type(usernameField, username)
            boolean usedField = false;
            for (Expression argument : call.getArguments()) {
                if (argument.isNameExpr()
                        && fields.containsKey(argument.asNameExpr().getNameAsString())) {
                    actions.add(new Action(callName,
                            fields.get(argument.asNameExpr().getNameAsString())));
                    usedField = true;
                }
            }
            if (usedField) {
                continue;
            }

            // case 3:  loginAs(...)  -> a call to another method of this class: follow it
            boolean ownCall = call.getScope().isEmpty() || call.getScope().get().isThisExpr();
            if (ownCall && methodsByName.containsKey(callName)) {
                actions.addAll(collectActions(
                        methodsByName.get(callName), fields, methodsByName, visiting));
            }
        }

        visiting.remove(method.getNameAsString());
        return actions;
    }

    /** field name -> locator, for the @FindBy fields of one page object file. */
    public Map<String, String> fieldsOf(Path javaFile) throws IOException {
        return readFindByFields(StaticJavaParser.parse(javaFile));
    }
}