package org.tuoyu.common.rpc.support;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 本地调用：进程内直接调用实现，不序列化、不经过网络。调用前后只做两件事，使行为与远程调用一致：
 * <ul>
 *     <li>调用方有事务时先挂起，提供方按自己的事务配置执行（跨模块调用不参与调用方事务）；</li>
 *     <li>异常原样抛出（不包装成反射异常）。</li>
 * </ul>
 * 实现在第一次调用时才从容器中获取，避免模块之间的循环依赖。
 *
 * @author tuoyu
 */
class LocalInvocationHandler implements InvocationHandler {

    private final Class<?> api;

    private final String targetBeanName;

    private final BeanFactory beanFactory;

    private final ObjectProvider<PlatformTransactionManager> transactionManager;

    private final boolean strictCopy;

    private volatile Object target;

    LocalInvocationHandler(Class<?> api, String targetBeanName, BeanFactory beanFactory,
                           ObjectProvider<PlatformTransactionManager> transactionManager, boolean strictCopy) {
        this.api = api;
        this.targetBeanName = targetBeanName;
        this.beanFactory = beanFactory;
        this.transactionManager = transactionManager;
        this.strictCopy = strictCopy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "LocalRemoteProxy[" + api.getName() + " -> " + targetBeanName + "]";
                default -> method.invoke(this, args);
            };
        }
        Object[] callArgs = strictCopy ? copyArgs(args) : args;
        Object result = withoutCallerTransaction(() -> invokeTarget(method, callArgs));
        return strictCopy ? copy(result) : result;
    }

    private Object invokeTarget(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target(), args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private Object target() {
        Object resolved = target;
        if (resolved == null) {
            resolved = beanFactory.getBean(targetBeanName);
            target = resolved;
        }
        return resolved;
    }

    /**
     * 调用方有事务时挂起它（相当于 NOT_SUPPORTED），提供方按自己的 @Transactional 开启新事务
     */
    private Object withoutCallerTransaction(Call call) throws Throwable {
        PlatformTransactionManager manager = TransactionSynchronizationManager.isActualTransactionActive()
            ? transactionManager.getIfUnique() : null;
        if (manager == null) {
            return call.run();
        }
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        try {
            return template.execute(status -> {
                try {
                    return call.run();
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new CheckedCarrier(e);
                }
            });
        } catch (CheckedCarrier e) {
            throw e.getCause();
        }
    }

    private Object[] copyArgs(Object[] args) {
        if (args == null) {
            return null;
        }
        Object[] copied = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            copied[i] = copy(args[i]);
        }
        return copied;
    }

    /**
     * 严格模式：通过序列化深拷贝，模拟远程调用中两边拿到的是不同对象
     */
    private Object copy(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Serializable)) {
            throw new IllegalStateException(api.getName() + " 的参数或返回值不可序列化：" + value.getClass().getName());
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(value);
            }
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
                @Override
                protected Class<?> resolveClass(java.io.ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    return Class.forName(desc.getName(), false, api.getClassLoader());
                }
            }) {
                return in.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("拷贝 " + api.getName() + " 的参数或返回值失败：" + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface Call {
        Object run() throws Throwable;
    }

    /**
     * 让受检异常穿过 TransactionTemplate
     */
    private static final class CheckedCarrier extends RuntimeException {
        CheckedCarrier(Throwable cause) {
            super(cause);
        }
    }

}
