### ReentrantLock Fair Mode

> When set `true`, under contention, locks favor granting access to the longest-waiting thread.

`true`로 설정하면 경합이 있을 때 락은 가장 오래 기다린 스레드에게 접근을 허용하는 쪽을 우선한다.  

> Programs using fair locks accessed by many threads may display lower overall throughput (i.e., are slower; often much slower) than those using the default setting, but have smaller variances in times to obtain locks and guarantee lack of starvation.

많은 스레드가 접근하는 공정 락을 쓰는 프로그램은 기본 설정보다 전체 처리량이 낮을 수 있다(종종 훨씬 느리다). 하지만 락을 얻는 시간의 편차가 작고, 기아가 없음을 보장한다.

> Note however, that fairness of locks does not guarantee fairness of thread scheduling.

다만 락의 공정성이 스레드 스케줄링의 공정성까지 보장하지는 않는다.

- 출처 [ReentrantLock, Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html)
- 계기: 