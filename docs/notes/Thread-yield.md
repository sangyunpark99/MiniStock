### Thread yield() Method

> A hint to the scheduler that the current thread is willing to yield its current use of a processor. The scheduler is free to ignore this hint.

현재 스레드가 프로세서 사용을 양보할 의향이 있다는 것을 스케줄러에게 알리는 힌트다. 스케줄러는 이 힌트를 무시해도 된다.

- 역할: 실행 중인 스레드가 OS 스케줄러에게 "지금 CPU를 다른 스레드에게 넘겨도 된다"고 알린다. 명령이 아니라 힌트라서, 실제로 양보될지는 스케줄러가 정한다.
- 쓰임: 일반 코드에서는 거의 쓰지 않고, 경쟁 조건 버그를 재현하는 테스트나 디버깅에 쓸 수 있다.
- 출처: [Thread, Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html)
- 계기: 공정 락 순서 역전 실험에서 도착 번호를 뽑은 뒤 `lock()`을 호출하기 전의 틈을 넓히려고 사용 ([실험 기록](../experiments/fair-lock-ordering.md))