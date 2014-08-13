package org.zhogov.annotationchecker;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Annotation processor intended to check "key", "condition" and "unless" fields of org.springframework.cache.annotation.Cacheable,
// org.springframework.cache.annotation.CacheEvict, org.springframework.cache.annotation.CachePut annotations.
//
// Those fields have references to parameters of annotated method. If Spring cannot find corresponding method parameter
// it will not throw ny exception and not found parameters will be null. For example:
//
// @Cacheable(value = "AuthorCache", key = "#id_author", unless = "#result==null")
// public AuthorEntity getEntity(Integer id)
//
// Here #id_author will be null, because SPEL evaluator will not be able to find it.
// Using this checker you will get compilation error pointing to getEntity method and #id_author variable.g
@SupportedAnnotationTypes({"org.springframework.cache.annotation.Cacheable",
        "org.springframework.cache.annotation.CacheEvict", "org.springframework.cache.annotation.CachePut",
        "org.springframework.cache.annotation.Caching"})
public class SpringCacheAnnotationChecker extends AbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        Messager messager = processingEnv.getMessager();
        // Get all code elements, annotated by configured annotations
        Set<Element> annotatedElements = new HashSet<Element>();
        for (TypeElement typeElement : annotations) {
            annotatedElements.addAll(env.getElementsAnnotatedWith(typeElement));
        }
        // Iterate over all annotated elements
        for (Element element : annotatedElements) {
            List<? extends javax.lang.model.element.VariableElement> params = null;
            if (element instanceof ExecutableElement) {
                ExecutableElement method = (ExecutableElement) element;

                checkCacheable(element.getAnnotation(Cacheable.class), method);
                checkCacheEvict(element.getAnnotation(CacheEvict.class), method);
                checkCachePut(element.getAnnotation(CachePut.class), method);
                checkCaching(element.getAnnotation(Caching.class), method);
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, "Cache annotation for element that is not method:" + element.toString());
            }
        }
        return true;
    }

    private void checkCacheable(Cacheable annotation, ExecutableElement method) {
        if (annotation != null) {
            check(annotation, annotation.key(), method);
            check(annotation, annotation.condition(), method);
            check(annotation, annotation.unless(), method);
        }
    }

    private void checkCacheEvict(CacheEvict annotation, ExecutableElement method) {
        if (annotation != null) {
            check(annotation, annotation.key(), method);
            check(annotation, annotation.condition(), method);
        }
    }

    private void checkCachePut(CachePut annotation, ExecutableElement method) {
        if (annotation != null) {
            check(annotation, annotation.key(), method);
            check(annotation, annotation.condition(), method);
            check(annotation, annotation.unless(), method);
        }
    }

    private void checkCaching(Caching annotation, ExecutableElement method) {
        if (annotation != null) {
            for (Cacheable child : annotation.cacheable()) {
                checkCacheable(child, method);
            }
            for (CacheEvict child : annotation.evict()) {
                checkCacheEvict(child, method);
            }
            for (CachePut child : annotation.put()) {
                checkCachePut(child, method);
            }
        }
    }

    // Check if parameters are present in method arguments
    // #result and #root are reserved by Spring
    private void check(Annotation annotation, String expression, ExecutableElement method) {
        List<? extends javax.lang.model.element.VariableElement> params = method.getParameters();
        Messager messager = processingEnv.getMessager();
        Pattern p = Pattern.compile("#[a-zA-Z_\\$][0-9a-zA-Z_\\$]*");
        Matcher m = p.matcher(expression);
        while (m.find()) {
            String key = expression.substring(m.start(), m.end());
            if (key.equals("#root") || key.equals("#result")) continue;
            // Cut # from beginning
            key = key.substring(1);

            boolean success = false;
            int i = 0;
            for (VariableElement var : params) {
                if (var.getSimpleName().contentEquals(key)) {
                    success = true;
                }
            }
            if (!success) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Key " + key + " for annotation " + annotation.toString() + " not found in parameters of method " + method.getReturnType().toString() + " " + method.toString());
            }
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
