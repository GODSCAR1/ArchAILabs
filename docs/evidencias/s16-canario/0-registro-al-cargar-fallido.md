# Acento en caché en CatalogPrices.java

**Session ID:** ses_e14078cd5ffeMshHupL2iGNFOZ
**Created:** 17/11/2026, 19:04:50
**Updated:** 17/11/2026, 19:08:06

---

## User

En el javadoc de src/main/java/com/codeja/master_acdc/catalog/api/CatalogPrices.java cambia "sin cache" por "sin caché".

---

## Assistant

_Thinking:_



**Tool: read**

**Input:**
```json
{
  "path": "src/main/java/com/codeja/master_acdc/catalog/api/CatalogPrices.java"
}
```

Read file src/main/java/com/codeja/master_acdc/catalog/api/CatalogPrices.java, lines 1-11
1: package com.codeja.master_acdc.catalog.api;
2: 
3: import java.util.List;
4: import java.util.UUID;
5: 
6: /** Lectura por lote de precios actuales del catálogo, sin cache. */
7: public interface CatalogPrices {
8:   List<ProductPrice> findByIds(List<UUID> ids);
9: 
10:   record ProductPrice(UUID id, String sku, long price) {}
11: }

---

## Assistant

**Tool: patch**

**Input:**
```json
{
  "patchText": "*** Begin Patch\n*** Update File: src/main/java/com/codeja/master_acdc/catalog/api/CatalogPrices.java\n@@\n-/** Lectura por lote de precios actuales del catálogo, sin cache. */\n+/** Lectura por lote de precios actuales del catálogo, sin caché. */\n*** End Patch\n"
}
```

Success. Updated the following files:
M src/main/java/com/codeja/master_acdc/catalog/api/CatalogPrices.java

---

## Assistant

Cambio aplicado. Ejecutaré la verificación obligatoria del proyecto.

**Tool: shell**

**Input:**
```json
{
  "command": "bash ./verify-quality.sh --fast",
  "timeout": 600000
}
```

	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation.proceed(InvocationInterceptorChain.java:99) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.proceed(InvocationInterceptorChain.java:66) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.chainAndInvoke(InvocationInterceptorChain.java:47) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.invoke(InvocationInterceptorChain.java:39) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:104) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:98) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invokeVoid(InterceptingExecutableInvoker.java:71) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$0(TestMethodTestDescriptor.java:219) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:215) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:157) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:70) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$2(NodeTestTask.java:176) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$1(NodeTestTask.java:166) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:138) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$0(NodeTestTask.java:164) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:163) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:116) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.exec(ForkJoinPoolHierarchicalTestExecutorService.java:253) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.execSync(ForkJoinPoolHierarchicalTestExecutorService.java:226) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService.executeSync(ForkJoinPoolHierarchicalTestExecutorService.java:168) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService.invokeAll(ForkJoinPoolHierarchicalTestExecutorService.java:140) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$2(NodeTestTask.java:180) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$1(NodeTestTask.java:166) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:138) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$0(NodeTestTask.java:164) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:163) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:116) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.exec(ForkJoinPoolHierarchicalTestExecutorService.java:253) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:511) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1450) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:2019) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:187) ~[na:na]

2026-11-17T19:06:27.104-05:00  WARN 111592 --- [master-acdc] [Pool-1-worker-9] c.c.m.c.i.a.o.cache.ProductDetailCache   : Shared product cache write failed

org.springframework.dao.DataAccessResourceFailureException: Redis offline
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductDetailCache.loadThroughRemote(ProductDetailCache.java:105) ~[classes/:na]
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductDetailCache.lambda$get$0(ProductDetailCache.java:76) ~[classes/:na]
	at org.springframework.cache.caffeine.CaffeineCache$LoadFunction.apply(CaffeineCache.java:231) ~[spring-context-support-7.0.9.jar:7.0.9]
	at com.github.benmanes.caffeine.cache.UnboundedLocalCache.lambda$computeIfAbsent$0(UnboundedLocalCache.java:280) ~[caffeine-3.2.4.jar:3.2.4]
	at java.base/java.util.concurrent.ConcurrentHashMap.computeIfAbsent(ConcurrentHashMap.java:1724) ~[na:na]
	at com.github.benmanes.caffeine.cache.UnboundedLocalCache.computeIfAbsent(UnboundedLocalCache.java:277) ~[caffeine-3.2.4.jar:3.2.4]
	at com.github.benmanes.caffeine.cache.LocalCache.computeIfAbsent(LocalCache.java:132) ~[caffeine-3.2.4.jar:3.2.4]
	at com.github.benmanes.caffeine.cache.LocalManualCache.get(LocalManualCache.java:63) ~[caffeine-3.2.4.jar:3.2.4]
	at org.springframework.cache.caffeine.CaffeineCache.get(CaffeineCache.java:133) ~[spring-context-support-7.0.9.jar:7.0.9]
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductDetailCache.get(ProductDetailCache.java:76) ~[classes/:na]
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductDetailCacheTest.should_count_l2_miss_once_when_shared_write_fails(ProductDetailCacheTest.java:199) ~[test-classes/:na]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:565) ~[na:na]
	at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:701) ~[junit-platform-commons-6.0.3.jar:6.0.3]
	at org.junit.platform.commons.support.ReflectionSupport.invokeMethod(ReflectionSupport.java:502) ~[junit-platform-commons-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.support.MethodReflectionUtils.invoke(MethodReflectionUtils.java:45) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:61) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation.proceed(InvocationInterceptorChain.java:124) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:163) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMethod(TimeoutExtension.java:148) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestMethod(TimeoutExtension.java:86) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker$ReflectiveInterceptorCall.lambda$ofVoidMethod$0(InterceptingExecutableInvoker.java:123) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.lambda$invoke$0(InterceptingExecutableInvoker.java:105) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation.proceed(InvocationInterceptorChain.java:99) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.proceed(InvocationInterceptorChain.java:66) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.chainAndInvoke(InvocationInterceptorChain.java:47) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.invoke(InvocationInterceptorChain.java:39) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:104) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:98) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invokeVoid(InterceptingExecutableInvoker.java:71) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$0(TestMethodTestDescriptor.java:219) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:215) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:157) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:70) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$2(NodeTestTask.java:176) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$1(NodeTestTask.java:166) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:138) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$0(NodeTestTask.java:164) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:163) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:116) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.exec(ForkJoinPoolHierarchicalTestExecutorService.java:253) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.execSync(ForkJoinPoolHierarchicalTestExecutorService.java:226) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService.executeSync(ForkJoinPoolHierarchicalTestExecutorService.java:168) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService.invokeAll(ForkJoinPoolHierarchicalTestExecutorService.java:140) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$2(NodeTestTask.java:180) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$1(NodeTestTask.java:166) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:138) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$0(NodeTestTask.java:164) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:163) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:116) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.exec(ForkJoinPoolHierarchicalTestExecutorService.java:253) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:511) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1450) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:2019) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:187) ~[na:na]

2026-11-17T19:06:27.173-05:00  WARN 111592 --- [master-acdc] [Pool-1-worker-9] c.c.m.c.i.a.o.cache.ProductDetailCache   : Incompatible shared product cache value; treating it as a miss
2026-11-17T19:06:27.488-05:00  WARN 111592 --- [master-acdc] [ool-1-worker-14] .m.c.i.a.o.c.ProductChangedCacheListener : Shared product cache eviction attempt 1 failed

org.springframework.dao.DataAccessResourceFailureException: Redis offline
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductChangedCacheListener.evictSharedWithRetry(ProductChangedCacheListener.java:68) ~[classes/:na]
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductChangedCacheListener.onProductChanged(ProductChangedCacheListener.java:45) ~[classes/:na]
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductChangedCacheListenerTest.should_invalidate_local_and_publish_when_shared_eviction_fails(ProductChangedCacheListenerTest.java:54) ~[test-classes/:na]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:565) ~[na:na]
	at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:701) ~[junit-platform-commons-6.0.3.jar:6.0.3]
	at org.junit.platform.commons.support.ReflectionSupport.invokeMethod(ReflectionSupport.java:502) ~[junit-platform-commons-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.support.MethodReflectionUtils.invoke(MethodReflectionUtils.java:45) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:61) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation.proceed(InvocationInterceptorChain.java:124) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:163) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMethod(TimeoutExtension.java:148) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestMethod(TimeoutExtension.java:86) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker$ReflectiveInterceptorCall.lambda$ofVoidMethod$0(InterceptingExecutableInvoker.java:123) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.lambda$invoke$0(InterceptingExecutableInvoker.java:105) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation.proceed(InvocationInterceptorChain.java:99) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.proceed(InvocationInterceptorChain.java:66) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.chainAndInvoke(InvocationInterceptorChain.java:47) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.invoke(InvocationInterceptorChain.java:39) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:104) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:98) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invokeVoid(InterceptingExecutableInvoker.java:71) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$0(TestMethodTestDescriptor.java:219) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:215) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:157) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:70) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$2(NodeTestTask.java:176) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$1(NodeTestTask.java:166) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:138) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$0(NodeTestTask.java:164) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:163) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:116) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.exec(ForkJoinPoolHierarchicalTestExecutorService.java:253) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.execSync(ForkJoinPoolHierarchicalTestExecutorService.java:226) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService.executeSync(ForkJoinPoolHierarchicalTestExecutorService.java:168) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService.invokeAll(ForkJoinPoolHierarchicalTestExecutorService.java:140) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$2(NodeTestTask.java:180) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$1(NodeTestTask.java:166) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:138) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$0(NodeTestTask.java:164) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:163) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:116) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.exec(ForkJoinPoolHierarchicalTestExecutorService.java:253) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:511) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1450) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:2019) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:187) ~[na:na]

2026-11-17T19:06:27.578-05:00  WARN 111592 --- [master-acdc] [ool-1-worker-14] .m.c.i.a.o.c.ProductChangedCacheListener : Shared product cache eviction attempt 2 failed

org.springframework.dao.DataAccessResourceFailureException: Redis offline
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductChangedCacheListener.evictSharedWithRetry(ProductChangedCacheListener.java:68) ~[classes/:na]
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductChangedCacheListener.onProductChanged(ProductChangedCacheListener.java:45) ~[classes/:na]
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductChangedCacheListenerTest.should_invalidate_local_and_publish_when_shared_eviction_fails(ProductChangedCacheListenerTest.java:54) ~[test-classes/:na]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:565) ~[na:na]
	at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:701) ~[junit-platform-commons-6.0.3.jar:6.0.3]
	at org.junit.platform.commons.support.ReflectionSupport.invokeMethod(ReflectionSupport.java:502) ~[junit-platform-commons-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.support.MethodReflectionUtils.invoke(MethodReflectionUtils.java:45) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:61) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation.proceed(InvocationInterceptorChain.java:124) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:163) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMethod(TimeoutExtension.java:148) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestMethod(TimeoutExtension.java:86) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker$ReflectiveInterceptorCall.lambda$ofVoidMethod$0(InterceptingExecutableInvoker.java:123) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.lambda$invoke$0(InterceptingExecutableInvoker.java:105) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation.proceed(InvocationInterceptorChain.java:99) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.proceed(InvocationInterceptorChain.java:66) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.chainAndInvoke(InvocationInterceptorChain.java:47) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.invoke(InvocationInterceptorChain.java:39) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:104) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:98) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invokeVoid(InterceptingExecutableInvoker.java:71) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$0(TestMethodTestDescriptor.java:219) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:215) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:157) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:70) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$2(NodeTestTask.java:176) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$1(NodeTestTask.java:166) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:138) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$0(NodeTestTask.java:164) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:163) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:116) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.exec(ForkJoinPoolHierarchicalTestExecutorService.java:253) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.execSync(ForkJoinPoolHierarchicalTestExecutorService.java:226) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService.executeSync(ForkJoinPoolHierarchicalTestExecutorService.java:168) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService.invokeAll(ForkJoinPoolHierarchicalTestExecutorService.java:140) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$2(NodeTestTask.java:180) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$1(NodeTestTask.java:166) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:138) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$0(NodeTestTask.java:164) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:163) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:116) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.exec(ForkJoinPoolHierarchicalTestExecutorService.java:253) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:511) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1450) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:2019) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:187) ~[na:na]

2026-11-17T19:06:27.861-05:00  WARN 111592 --- [master-acdc] [ool-1-worker-14] .m.c.i.a.o.c.ProductChangedCacheListener : Shared product cache eviction attempt 3 failed

org.springframework.dao.DataAccessResourceFailureException: Redis offline
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductChangedCacheListener.evictSharedWithRetry(ProductChangedCacheListener.java:68) ~[classes/:na]
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductChangedCacheListener.onProductChanged(ProductChangedCacheListener.java:45) ~[classes/:na]
	at com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache.ProductChangedCacheListenerTest.should_invalidate_local_and_publish_when_shared_eviction_fails(ProductChangedCacheListenerTest.java:54) ~[test-classes/:na]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:565) ~[na:na]
	at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:701) ~[junit-platform-commons-6.0.3.jar:6.0.3]
	at org.junit.platform.commons.support.ReflectionSupport.invokeMethod(ReflectionSupport.java:502) ~[junit-platform-commons-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.support.MethodReflectionUtils.invoke(MethodReflectionUtils.java:45) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:61) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation.proceed(InvocationInterceptorChain.java:124) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:163) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMethod(TimeoutExtension.java:148) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestMethod(TimeoutExtension.java:86) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker$ReflectiveInterceptorCall.lambda$ofVoidMethod$0(InterceptingExecutableInvoker.java:123) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.lambda$invoke$0(InterceptingExecutableInvoker.java:105) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation.proceed(InvocationInterceptorChain.java:99) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.proceed(InvocationInterceptorChain.java:66) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.chainAndInvoke(InvocationInterceptorChain.java:47) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.invoke(InvocationInterceptorChain.java:39) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:104) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:98) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invokeVoid(InterceptingExecutableInvoker.java:71) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$0(TestMethodTestDescriptor.java:219) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:215) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:157) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:70) ~[junit-jupiter-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$2(NodeTestTask.java:176) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$1(NodeTestTask.java:166) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:138) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$0(NodeTestTask.java:164) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:163) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:116) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.exec(ForkJoinPoolHierarchicalTestExecutorService.java:253) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.execSync(ForkJoinPoolHierarchicalTestExecutorService.java:226) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService.executeSync(ForkJoinPoolHierarchicalTestExecutorService.java:168) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService.invokeAll(ForkJoinPoolHierarchicalTestExecutorService.java:140) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$2(NodeTestTask.java:180) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$1(NodeTestTask.java:166) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:138) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$0(NodeTestTask.java:164) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:74) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:163) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:116) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at org.junit.platform.engine.support.hierarchical.ForkJoinPoolHierarchicalTestExecutorService$ExclusiveTask.exec(ForkJoinPoolHierarchicalTestExecutorService.java:253) ~[junit-platform-engine-6.0.3.jar:6.0.3]
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:511) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1450) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:2019) ~[na:na]
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:187) ~[na:na]

2026-11-17T19:06:27.879-05:00 ERROR 111592 --- [master-acdc] [ool-1-worker-14] .m.c.i.a.o.c.ProductChangedCacheListener : Shared product cache eviction failed after retries
2026-11-17T19:06:28.205-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-7] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for test class [com.codeja.master_acdc.identity.infrastructure.adapters.in.web.PasswordResetControllerTest]: PasswordResetControllerTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-11-17T19:06:28.734-05:00  WARN 111592 --- [master-acdc] [Pool-1-worker-9] c.c.m.i.i.a.o.s.SessionVersionValidator  : Session version validation rejected: shared state unavailable or invalid
2026-11-17T19:06:28.751-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-7] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.codeja.master_acdc.MasterAcdcApplication for test class com.codeja.master_acdc.identity.infrastructure.adapters.in.web.PasswordResetControllerTest
2026-11-17T19:06:28.753-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-7] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for test class [com.codeja.master_acdc.identity.infrastructure.adapters.in.web.PasswordResetControllerTest]: PasswordResetControllerTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-11-17T19:06:28.803-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-7] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.codeja.master_acdc.MasterAcdcApplication for test class com.codeja.master_acdc.identity.infrastructure.adapters.in.web.PasswordResetControllerTest
2026-11-17T19:06:29.651-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-1] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for test class [com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.OrderControllerTest]: OrderControllerTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-11-17T19:06:30.288-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-1] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.codeja.master_acdc.MasterAcdcApplication for test class com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.OrderControllerTest
2026-11-17T19:06:30.290-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-1] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for test class [com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.OrderControllerTest]: OrderControllerTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.1)

2026-11-17T19:06:30.335-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-1] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.codeja.master_acdc.MasterAcdcApplication for test class com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.OrderControllerTest
2026-11-17T19:06:30.339-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-14] o.s.boot.SpringApplication               : Starting application using Java 25.0.4.1 with PID 111592 (started by oscar in C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc)
2026-11-17T19:06:30.340-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-14] o.s.boot.SpringApplication               : No active profile set, falling back to 1 default profile: "default"
2026-11-17T19:06:30.735-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-14] o.s.boot.SpringApplication               : Started application in 1.567 seconds (process running for 9.205)

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.1)

2026-11-17T19:06:31.014-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-14] o.s.boot.SpringApplication               : Starting application using Java 25.0.4.1 with PID 111592 (started by oscar in C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc)
2026-11-17T19:06:31.014-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-14] o.s.boot.SpringApplication               : No active profile set, falling back to 1 default profile: "default"
2026-11-17T19:06:31.171-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-14] o.s.boot.SpringApplication               : Started application in 0.42 seconds (process running for 9.642)
2026-11-17T19:06:32.229-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-12] o.s.b.t.m.w.SpringBootMockServletContext : Initializing Spring TestDispatcherServlet ''
2026-11-17T19:06:32.229-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-12] o.s.t.web.servlet.TestDispatcherServlet  : Initializing Servlet ''
2026-11-17T19:06:32.232-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-12] o.s.t.web.servlet.TestDispatcherServlet  : Completed initialization in 3 ms
2026-11-17T19:06:32.305-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-12] c.c.m.c.i.a.i.web.ProductControllerTest  : Started ProductControllerTest in 6.692 seconds (process running for 10.776)

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.1)

2026-11-17T19:06:32.348-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-8] .c.m.e.i.a.i.w.AdminExportControllerTest : Starting AdminExportControllerTest using Java 25.0.4.1 with PID 111592 (started by oscar in C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc)
2026-11-17T19:06:32.348-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-8] .c.m.e.i.a.i.w.AdminExportControllerTest : No active profile set, falling back to 1 default profile: "default"
2026-11-17T19:06:33.034-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-8] o.s.b.t.m.w.SpringBootMockServletContext : Initializing Spring TestDispatcherServlet ''
2026-11-17T19:06:33.035-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-8] o.s.t.web.servlet.TestDispatcherServlet  : Initializing Servlet ''
2026-11-17T19:06:33.036-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-8] o.s.t.web.servlet.TestDispatcherServlet  : Completed initialization in 1 ms
2026-11-17T19:06:33.101-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-8] .c.m.e.i.a.i.w.AdminExportControllerTest : Started AdminExportControllerTest in 0.792 seconds (process running for 11.571)

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.1)

2026-11-17T19:06:33.141-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-13] .m.c.i.a.i.w.ProductDetailControllerTest : Starting ProductDetailControllerTest using Java 25.0.4.1 with PID 111592 (started by oscar in C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc)
2026-11-17T19:06:33.141-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-13] .m.c.i.a.i.w.ProductDetailControllerTest : No active profile set, falling back to 1 default profile: "default"
2026-11-17T19:06:33.730-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-13] o.s.b.t.m.w.SpringBootMockServletContext : Initializing Spring TestDispatcherServlet ''
2026-11-17T19:06:33.731-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-13] o.s.t.web.servlet.TestDispatcherServlet  : Initializing Servlet ''
2026-11-17T19:06:33.731-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-13] o.s.t.web.servlet.TestDispatcherServlet  : Completed initialization in 0 ms
2026-11-17T19:06:33.739-05:00  INFO 111592 --- [master-acdc] [ool-1-worker-13] .m.c.i.a.i.w.ProductDetailControllerTest : Started ProductDetailControllerTest in 0.635 seconds (process running for 12.21)

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.1)

2026-11-17T19:06:33.780-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-7] .m.i.i.a.i.w.PasswordResetControllerTest : Starting PasswordResetControllerTest using Java 25.0.4.1 with PID 111592 (started by oscar in C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc)
2026-11-17T19:06:33.780-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-7] .m.i.i.a.i.w.PasswordResetControllerTest : No active profile set, falling back to 1 default profile: "default"
2026-11-17T19:06:34.344-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-7] o.s.b.t.m.w.SpringBootMockServletContext : Initializing Spring TestDispatcherServlet ''
2026-11-17T19:06:34.344-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-7] o.s.t.web.servlet.TestDispatcherServlet  : Initializing Servlet ''
2026-11-17T19:06:34.344-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-7] o.s.t.web.servlet.TestDispatcherServlet  : Completed initialization in 0 ms
2026-11-17T19:06:34.364-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-7] .m.i.i.a.i.w.PasswordResetControllerTest : Started PasswordResetControllerTest in 0.613 seconds (process running for 12.835)

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.1)

2026-11-17T19:06:34.386-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-1] c.c.m.o.i.a.in.web.OrderControllerTest   : Starting OrderControllerTest using Java 25.0.4.1 with PID 111592 (started by oscar in C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc)
2026-11-17T19:06:34.386-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-1] c.c.m.o.i.a.in.web.OrderControllerTest   : No active profile set, falling back to 1 default profile: "default"
2026-11-17T19:06:35.039-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-1] o.s.b.t.m.w.SpringBootMockServletContext : Initializing Spring TestDispatcherServlet ''
2026-11-17T19:06:35.039-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-1] o.s.t.web.servlet.TestDispatcherServlet  : Initializing Servlet ''
2026-11-17T19:06:35.041-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-1] o.s.t.web.servlet.TestDispatcherServlet  : Completed initialization in 2 ms
2026-11-17T19:06:35.051-05:00  INFO 111592 --- [master-acdc] [Pool-1-worker-1] c.c.m.o.i.a.in.web.OrderControllerTest   : Started OrderControllerTest in 0.684 seconds (process running for 13.522)
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
▶ Contextos de Spring en los IT (lab S16)
  1 contexto(s) de integración
▶ Capa 3: mutation testing de pricing (lab S15, docs/lab-s15.md)
  (sin cambios en pricing respecto a origin/main: se ejecuta en --full)

[showing lines 310-689 of 689; full output saved to C:\Users\oscar\.local\share\opencode\shell\7321ce18ae6034e160725e6fcbb35496d5f7850f\sh_1ebf8b26800143Ghe5UsBbeFuu.out]

---

## Assistant

Cambiado «sin cache» por «sin caché» en el Javadoc de `CatalogPrices.java`.

Verificación `--fast` superada. No se ejecutó la verificación completa.
