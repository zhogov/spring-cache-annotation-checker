spring-cache-annotation-checker
===============================

Spring cache compile-time annotation processor

Annotation processor intended to check "key", "condition" and "unless" fields of 
org.springframework.cache.annotation.Cacheable,
org.springframework.cache.annotation.CacheEvict, 
org.springframework.cache.annotation.CachePut,
org.springframework.cache.annotation.Caching
annotations.

Those fields have references to parameters of annotated method. If Spring cannot find corresponding method parameter
it will not throw ny exception and not found parameters will be null. For example:

@Cacheable(value = "AuthorCache", key = "#id_author", unless = "#result==null")
public AuthorEntity getEntity(Integer id) {
...
}

Here #id_author will be null, because SPEL evaluator will not be able to find it.
Using this checker you will get compilation error pointing to getEntity method and #id_author variable.

