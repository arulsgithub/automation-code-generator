package com.autogen.indexer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MethodScanner {

    public List<MethodInfo> scan(Path javaFile) throws IOException {
        CompilationUnit tree = StaticJavaParser.parse(javaFile);
        String className = tree.getPrimaryTypeName().orElse("Unknown");
        List<MethodDeclaration> methods = tree.findAll(MethodDeclaration.class);

        List<MethodInfo> result = new ArrayList<>();
        for (MethodDeclaration method : methods) {
            if (!method.isPublic()) {
                continue;
            }
            List<String> params = new ArrayList<>();
            for (Parameter parameter : method.getParameters()) {
                params.add(parameter.getType().asString() + " " + parameter.getNameAsString());
            }
            String returnType = method.getType().asString();
            String javadoc = method.getJavadoc()
                    .map(doc -> doc.getDescription().toText().strip())
                    .orElse("");
            result.add(new MethodInfo(className, method.getNameAsString(),
                    params, returnType, javadoc));
        }
        return result;
    }
}