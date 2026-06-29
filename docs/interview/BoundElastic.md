**boundedElastic** — это **динамический пул потоков с ограничением**, а не один поток и не просто `new Thread()` каждый раз. 
  - По умолчанию он создаёт до `10 * число_ядер` рабочих потоков и ставит лишние задачи в очередь [https://stackoverflow.com/questions/61304762/difference-between-boundedelastic-vs-parallel-scheduler](https://stackoverflow.com/questions/61304762/difference-between-boundedelastic-vs-parallel-scheduler) [https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Schedulers.html](https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Schedulers.html) [https://docs.spring.io/projectreactor/reactor-core/docs/3.7.0-M3/reference/html/coreFeatures/schedulers.html](https://docs.spring.io/projectreactor/reactor-core/docs/3.7.0-M3/reference/html/coreFeatures/schedulers.html)

## Сколько это потоков

По документации Reactor / Spring:

  - boundedElastic использует **пул потоков**, размер которого по умолчанию равен `10 * availableProcessors` (можно менять системным свойством `reactor.schedulers.defaultBoundedElasticSize`) [https://github.com/reactor/reactor-core/issues/3857](https://github.com/reactor/reactor-core/issues/3857) [https://stackoverflow.com/questions/61304762/difference-between-boundedelastic-vs-parallel-scheduler](https://stackoverflow.com/questions/61304762/difference-between-boundedelastic-vs-parallel-scheduler)
  - На каждый поток приходится своя очередь задач (по умолчанию до 100000 задач на поток) [https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Schedulers.html](https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Schedulers.html) [https://stackoverflow.com/questions/61304762/difference-between-boundedelastic-vs-parallel-scheduler](https://stackoverflow.com/questions/61304762/difference-between-boundedelastic-vs-parallel-scheduler)
  - Потоки **пере-используются**: если **поток** освободился, его берут под следующую блокирующую задачу, а если простаивает долго — могут убрать [https://www.youtube.com/watch?v=Xpoa7HRAhlU](https://www.youtube.com/watch?v=Xpoa7HRAhlU) [https://blog.csdn.net/mowushenght/article/details/123535385](https://blog.csdn.net/mowushenght/article/details/123535385)

То есть “много” — это **ограниченное “много”**, а не бесконечное число новых `Thread` [https://stackoverflow.com/questions/61304762/difference-between-boundedelastic-vs-parallel-scheduler](https://stackoverflow.com/questions/61304762/difference-between-boundedelastic-vs-parallel-scheduler)

## Как это представить

Представь:

- есть **N рабочих столов** (потоков);
- если приходит новая блокирующая задача:
    - если есть свободный стол, задача садится туда;
    - если нет, но ещё можно добавить столы (не достигнут лимит), создаётся новый;
    - если лимит достигнут, задача встаёт в очередь и ждёт, пока кто-то освободится [https://eherrera.net/project-reactor-course/06-schedulers-and-threads/what-is-a-scheduler.html](https://eherrera.net/project-reactor-course/06-schedulers-and-threads/what-is-a-scheduler.html) [https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html)

**boundedElastic** _**не event loop**_, который ждёт событий от **селектора**. Это **обычный “умный” thread pool**, адаптированный под блокирующие задачи [https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html) [https://habr.com/ru/articles/694850/](https://habr.com/ru/articles/694850/)

## Чем boundedElastic отличается от просто new Thread()

  - `new Thread()` — каждый раз новый поток, без повторного использования, без лимитов. Легко убить систему тысячами потоков [https://blog.stackademic.com/error-handling-publishers-schedulers-and-backpressure-phase-3-module-3-schedulers-d26e53bbacd4](https://blog.stackademic.com/error-handling-publishers-schedulers-and-backpressure-phase-3-module-3-schedulers-d26e53bbacd4)
  - _**boundedElastic**_ — даёт тебе **готовый, пере-используемый, ограниченный пул**, где Reactor сам следит, сколько потоков создать и когда их гасить [https://github.com/reactor/reactor-core/issues/1804](https://github.com/reactor/reactor-core/issues/1804) [https://stackoverflow.com/questions/61304762/difference-between-boundedelastic-vs-parallel-scheduler](https://stackoverflow.com/questions/61304762/difference-between-boundedelastic-vs-parallel-scheduler) [https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html)

Поэтому, когда ты пишешь `publishOn(Schedulers.boundedElastic())`:

 - твоя цепочка “переезжает” на **один из потоков** этого пула;
 - блокирующая операция выполняется там;
 - после завершения поток может быть использован для других задач [https://docs.spring.io/projectreactor/reactor-core/docs/3.7.0-M3/reference/html/coreFeatures/schedulers.html](https://docs.spring.io/projectreactor/reactor-core/docs/3.7.0-M3/reference/html/coreFeatures/schedulers.html) [https://toparvion.pro/post/2021/reactivlet-5-feign/](https://toparvion.pro/post/2021/reactivlet-5-feign/)