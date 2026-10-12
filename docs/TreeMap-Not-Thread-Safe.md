### TreeMap은 Thread-Safe 하지 않음

> Note that this implementation is not synchronized. If multiple threads access a map concurrently, and at least one of the threads modifies the map structurally, it must be synchronized externally.

이 구현은 동기화되어 있지 않다. 여러 스레드가 동시에 맵에 접근하고, 그중 하나라도 맵의 구조를 바꾼다면 외부에서 동기화해야 한다.


- 출처: [TreeMap, Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/TreeMap.html)
- 계기: 동시성 테스트에서 트리가 깨져 스레드가 `fixAfterDeletion` 에서 멈춘 것을 보고 확인

