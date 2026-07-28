package com.example.reactivedemo.service;

import com.example.reactivedemo.domain.User;
import com.example.reactivedemo.dto.MapVsFlatMapComparisonResponse;
import com.example.reactivedemo.dto.MapVsFlatMapComparisonResponse.FlatMapCorrectResult;
import com.example.reactivedemo.dto.MapVsFlatMapComparisonResponse.MapWrongResult;
import com.example.reactivedemo.dto.UserResponse;
import com.example.reactivedemo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * <p>Учебный сервис: наглядно показывает разницу между {@code map} и {@code flatMap}
 * на том же {@link UserRepository}.</p>
 *
 * <p>В production-коде используйте подход из {@link UserService}.
 * Методы с суффиксом «Wrong» — только для демонстрации антипаттерна.</p>
 */
@Service
@RequiredArgsConstructor
public class ReactorDemoService {

    private final UserRepository userRepository;

    /**
     * <p><strong>Антипаттерн:</strong> {@code map} + метод репозитория, возвращающий {@link Mono}.</p>
     *
     * <p>Что происходит:</p>
     * <ul>
     *   <li>{@code findById} возвращает {@link Mono}{@code <User>}, а не {@link User};</li>
     *   <li>{@code map} кладёт объект {@link Mono} в поток как обычное значение;</li>
     *   <li>итоговый тип — {@link Flux}{@code <Mono<User>>}, а не {@link Flux}{@code <User>};</li>
     *   <li>запрос в БД не выполняется, пока на внутренний {@link Mono} не подпишутся.</li>
     * </ul>
     *
     * <p>Неправильно:</p>
     * <pre>
     * {@code
     * Flux.fromIterable(ids)
     *     .map(userRepository::findById);
     * }
     * </pre>
     *
     * @param ids список идентификаторов пользователей
     * @return поток объектов {@link Mono}, а не пользователей
     */
    public Flux<Mono<User>> loadUsersWithMapWrong(List<Long> ids) {

        Flux<User> allById = userRepository.findAllById(ids);
        Flux<String> map = allById.map(
                user -> user.email());

        Mono<User> byId = userRepository.findById(1L);

        return Flux.fromIterable(ids)
                .map(userRepository::findById);
    }

    public void m() {



    }

    /**
     * <p><strong>Правильно:</strong> {@code flatMap} подписывается на {@link Mono} от репозитория
     * и передаёт дальше уже {@link User}.</p>
     *
     * <p>Пример:</p>
     * <pre>
     * {@code
     * Flux.fromIterable(ids)
     *     .flatMap(userRepository::findById)
     *     .map(UserResponse::from);
     * }
     * </pre>
     *
     * @param ids список идентификаторов пользователей
     * @return {@link Flux} DTO найденных пользователей
     */
    public Flux<UserResponse> loadUsersWithFlatMapCorrect(List<Long> ids) {
        return Flux.fromIterable(ids)
                .flatMap(userRepository::findById)
                .map(UserResponse::from);
    }

    /**
     * <p>Тот же сценарий, что {@link #loadUsersWithFlatMapCorrect}, но через {@code concatMap}:
     * запросы в БД идут <strong>строго по очереди</strong> (порядок id сохраняется на выходе).</p>
     *
     * @param ids список идентификаторов пользователей
     * @return {@link Flux} DTO в порядке id
     */
    public Flux<UserResponse> loadUsersWithConcatMap(List<Long> ids) {
        return Flux.fromIterable(ids)
                .concatMap(userRepository::findById)
                .map(UserResponse::from);
    }

    /**
     * <p>Сравнение для REST: тип элементов потока при {@code map} и реальные данные при {@code flatMap}.</p>
     *
     * <p>Используется endpoint {@code GET /api/demo/reactor/compare?ids=1,2}.</p>
     *
     * @param ids идентификаторы для демонстрации
     * @return {@link Mono} с результатами сравнения для JSON-ответа
     */
    public Mono<MapVsFlatMapComparisonResponse> compareMapVsFlatMap(List<Long> ids) {
        Flux<Mono<User>> wrongFlux = loadUsersWithMapWrong(ids);
        Flux<UserResponse> correctFlux = loadUsersWithConcatMap(ids);

        Mono<List<String>> wrongElementTypes = wrongFlux
                .map(mono -> mono.getClass().getSimpleName())
                .collectList();

        Mono<List<UserResponse>> correctUsers = correctFlux.collectList();

        return Mono.zip(wrongElementTypes, correctUsers)
                .map(tuple -> new MapVsFlatMapComparisonResponse(
                        "Один и тот же userRepository.findById(id): map кладёт Mono в поток, flatMap подписывается и отдаёт User.",
                        new MapWrongResult(
                                "Mono<User>",
                                tuple.getT1(),
                                List.of(),
                                "Без flatMap в поток попадает Mono (холодный Publisher). "
                                        + "Запрос в БД не выполняется, пока на Mono не подпишутся."
                        ),
                        new FlatMapCorrectResult(
                                "User",
                                tuple.getT2()
                        )
                ));
    }
}
