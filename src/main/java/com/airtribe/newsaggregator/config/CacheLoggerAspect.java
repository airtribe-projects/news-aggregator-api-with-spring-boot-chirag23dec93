package com.airtribe.newsaggregator.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CacheLoggerAspect {
    
    private static final Logger log = LoggerFactory.getLogger(CacheLoggerAspect.class);

    @Around("@annotation(cacheable)")
    public Object logCacheAccess(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long endTime = System.currentTimeMillis();
        
        log.info("Cache operation - Class: {}, Method: {}, Args: {}, Execution Time: {}ms",
                className, methodName, args, (endTime - startTime));
        
        return result;
    }
}
