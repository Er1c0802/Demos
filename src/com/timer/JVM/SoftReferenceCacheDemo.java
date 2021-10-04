package com.timer;

import com.sun.org.apache.xpath.internal.operations.Or;
import org.omg.CORBA.ORB;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 总体思路：
 * JVM最大堆为1g
 * Order填充空间，一个128M。
 * OrderCacheInterface规定了缓存的几个常见用法
 * OrderCache使用DCL单例模式，结合ReferenceQueue监控软引用的情况，每次put之前清除脏entry。
 * OrderReference 继承了弱引用，同时注册了ReferenceQueue。
 * OrderCacheManage 写了一些OrderCache的常用操作，方便主函数中直接调用
 */

class Order {
    public byte[] bytes;

    public String id;

    Order(String id) {
        this.id = id;
        this.bytes = new byte[1024 * 1024 * 128];
    }

    public String getId() {
        return id;
    }
}

interface OrderCacheInterface {

    void putIntoCache(Order order);

    Order getFromCache(String id);

    void clearCache();

}

class OrderCache implements OrderCacheInterface {

    private ReferenceQueue<Order> referenceQueue;

    private Map<String, OrderReference> cache;

    OrderCache() {
        this.referenceQueue = new ReferenceQueue<>();
        this.cache = new HashMap<>(1024);
    }


    private static volatile OrderCache singletonCache;

    //DCL单例，线程安全，懒加载，高性能
    public static OrderCache getInstance() {
        if (singletonCache == null) {
            synchronized (OrderCache.class) {
                if (singletonCache == null) {
                    singletonCache = new OrderCache();
                }
            }
        }
        return singletonCache;
    }

    @Override
    public void putIntoCache(Order order) {
        clearCache();
        OrderReference orderReference = new OrderReference(order, referenceQueue);
        cache.put(orderReference.id, orderReference);
        System.out.println(new Date() + "  put order : " + orderReference.id + " into cache");
    }

    @Override
    public Order getFromCache(String id) {
        Order order = null;
        if (cache.containsKey(id)) {
            order = cache.get(id).get();
            System.out.println(new Date() + "  get order : " + order.getId() + " from cache");
        }
        return order;
    }

    @Override
    public void clearCache() {
        OrderReference reference = null;
        while ((reference = (OrderReference) referenceQueue.poll()) != null) {
            System.out.println(new Date() + "  clean order : " + reference.id + " from cache");
            cache.remove(reference.id);
        }
    }


    static class OrderReference extends SoftReference<Order> {
        String id;

        public OrderReference(Order referent, ReferenceQueue referenceQueue) {
            super(referent, referenceQueue);
            this.id = referent.getId();
        }
    }
}

class OrderCacheManage {
    public Order getOrder(String id) {
        Order order = OrderCache.getInstance().getFromCache(id);
        if (order == null) {
            order = getOrderFromDB(id);
        }
        return order;
    }

    public Order getOrderFromDB(String id) {
        Order order = new Order(id);
        System.out.println(new Date() + " get order : " + order.id + " from db");
        OrderCache.getInstance().putIntoCache(order);
        return order;
    }
}


class SoftReferenceCacheDemo {
    public static void main(String[] args) {
        OrderCacheManage orderCacheManage = new OrderCacheManage();
        new Thread(() -> {
            System.out.println("开始注入缓存");
            for (int i = 0; i < 10; i++) {
                orderCacheManage.getOrder(String.valueOf(i));
                quietlySleep(250);
            }
            System.out.println("开始从缓存中获得");
            while (true) {
                int requestId = new Random().nextInt(13);
                orderCacheManage.getOrder(String.valueOf(requestId));
                int free = (int) (Runtime.getRuntime().freeMemory() / 1024 / 1024);
                System.out.println("Free memory : " + free + " m");
                quietlySleep(500);
            }
        }).start();
    }

    private static void quietlySleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

