package com.example.greeting.projection.service;

import com.example.greeting.projection.entity.Customer;
import com.example.greeting.projection.entity.Order;
import com.example.greeting.projection.entity.Product;
import com.example.greeting.projection.repository.CustomerRepository;
import com.example.greeting.projection.repository.OrderItemRepository;
import com.example.greeting.projection.repository.OrderRepository;
import com.example.greeting.projection.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Учебный сервис интернет-магазина.
 *
 * <p>Служит точкой входа для всех примеров по проекциям Spring Data JPA.
 * Методы разделены по транзакционным границам:</p>
 * <ul>
 *   <li>Методы чтения аннотированы {@code @Transactional(readOnly = true)}.
 *       Флаг {@code readOnly} сообщает Hibernate, что в рамках этой транзакции
 *       изменений не ожидается: Hibernate не будет отслеживать состояние
 *       загруженных объектов и не будет делать flush перед закрытием сессии.
 *       Это снижает расход памяти и CPU при больших выборках.</li>
 *   <li>Методы записи аннотированы {@code @Transactional} без флага.
 *       Hibernate отслеживает изменения загруженных сущностей и при коммите
 *       автоматически генерирует UPDATE для изменённых полей.</li>
 * </ul>
 *
 * @implNote Spring AOP при старте приложения создаёт CGLIB-прокси поверх
 *           {@code ShopService}. С точки зрения вызывающего кода (контроллера
 *           или теста) переменная типа {@code ShopService} фактически
 *           содержит этот прокси-объект, а не сам сервис.
 *           <p>Когда контроллер вызывает {@code shopService.findAllOrders()},
 *           вызов попадает в прокси. Прокси открывает транзакцию, затем
 *           передаёт вызов реальному методу {@code ShopService}, а после
 *           его завершения выполняет commit или rollback.
 *           <p><b>Типичная ошибка — self-invocation.</b> Если внутри
 *           {@code ShopService} один метод вызывает другой метод того же
 *           класса напрямую: {@code this.findAllOrders()}, — вызов идёт
 *           в реальный объект, минуя прокси. Прокси в этом случае не
 *           задействован, и транзакция, объявленная на {@code findAllOrders()},
 *           не откроется. Аннотация {@code @Transactional} есть, но она
 *           не сработает, потому что транзакционная логика живёт в прокси,
 *           а не в самом классе.
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    // ----------------------------------------------------------
    // Customer
    // ----------------------------------------------------------

    /**
     * Возвращает всех покупателей в виде полных сущностей {@link Customer}.
     *
     * <p>Hibernate выполняет {@code SELECT * FROM shop_demo.customer}
     * и помещает каждый загруженный объект в Persistence Context —
     * внутренний кеш сессии. Пока транзакция открыта, Hibernate помнит
     * исходное состояние каждого объекта.</p>
     *
     * <p>Поле {@code orders} в {@link Customer} объявлено как
     * {@code FetchType.LAZY}. Это означает: при выполнении
     * {@code findAllCustomers()} SQL-запрос за заказами покупателя
     * <b>не выполняется</b>. Вместо реального списка заказов Hibernate
     * подставляет прокси-заглушку. Обращение к {@code customer.getOrders()}
     * внутри транзакции инициирует отдельный SELECT для каждого покупателя.
     * Обращение к {@code customer.getOrders()} после того, как метод
     * завершился и транзакция закрылась, приведёт к исключению
     * {@code LazyInitializationException}: сессия уже закрыта и выполнить
     * запрос невозможно.</p>
     *
     * @return список всех покупателей без загрузки связанных заказов
     */
    @Transactional(readOnly = true)
    public List<Customer> findAllCustomers() {
        return customerRepository.findAll();
    }

    // ----------------------------------------------------------
    // Product
    // ----------------------------------------------------------

    /**
     * Возвращает все товары в виде полных сущностей {@link Product}.
     *
     * <p>Hibernate загружает все скалярные поля каждого товара, включая
     * {@code description}. Ленивые коллекции {@code prices} и
     * {@code orderItems} не запрашиваются из БД по тем же причинам,
     * что описаны в {@link #findAllCustomers()}.</p>
     *
     * <p>В следующих под-темах будет показано, как через проекции
     * исключить из SELECT неиспользуемые поля (например, {@code description})
     * и получить только те столбцы, которые реально нужны вызывающему коду.</p>
     *
     * @return список всех товаров без загрузки связанных цен и строк заказов
     */
    @Transactional(readOnly = true)
    public List<Product> findAllProducts() {
        return productRepository.findAll();
    }

    // ----------------------------------------------------------
    // Order
    // ----------------------------------------------------------

    /**
     * Возвращает все заказы в виде полных сущностей {@link Order}.
     *
     * <p>Hibernate выполняет {@code SELECT * FROM shop_demo.order} и
     * загружает скалярные поля каждого заказа: {@code id}, {@code status},
     * {@code createdAt}, {@code updatedAt}. Поля-ассоциации при этом
     * не загружаются:</p>
     * <ul>
     *   <li>{@code customer} объявлен как {@code ManyToOne LAZY}.
     *       Данные покупателя в этот момент не запрашиваются из БД.
     *       Обращение к {@code order.getCustomer().getEmail()} внутри
     *       транзакции приведёт к отдельному SELECT по {@code customer_id}
     *       для каждого заказа.</li>
     *   <li>{@code items} объявлен как {@code OneToMany LAZY}.
     *       Строки заказа не загружаются. Обращение к {@code order.getItems()}
     *       внутри транзакции выполнит отдельный SELECT по {@code order_id}
     *       для каждого заказа. Если заказов 50, это 50 дополнительных
     *       запросов — классическая проблема N+1.</li>
     * </ul>
     *
     * <p>Метод намеренно оставлен в таком виде как отправная точка.
     * В следующих под-темах будет показано, как проекции позволяют
     * получить только нужные поля (например, {@code id} и {@code status})
     * единственным SQL-запросом — без риска N+1 и без помещения сущностей
     * в Persistence Context.</p>
     *
     * @return список всех заказов без загрузки покупателя и строк заказа
     */
    @Transactional(readOnly = true)
    public List<Order> findAllOrders() {
        return orderRepository.findAll();
    }
}