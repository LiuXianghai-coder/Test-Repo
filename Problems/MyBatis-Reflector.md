# MyBatis 使用—反射工厂

一般来讲，使用 `MyBatis` 默认的 `setter` 方法规则获取对应属性值来构造动态 SQL 在日常使用中都是可行的。然而，在某些情况下，可能不得不改变这个默认的行为。比如说：希望优先使用接口中定义的方法名直接获取对应的属性值，这在设计通用接口的时候可能会遇到

结合 `MyBatis` 执行流程对应的源代码：

``` java
public class DefaultParameterHandler implements ParameterHandler {
    // 省略部分代码

    @Override
    public void setParameters(PreparedStatement ps) {
        ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings != null) {
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    Object value;
                    String propertyName = parameterMapping.getProperty();
                    // 省略部分其它的处理逻辑
                    /*
                    	通过 getter 方法获取参数值的处理是在这里进行的，具体的处理也是交给 MyBatis 内置的 ReflctFactory 进行
                    */
                    MetaObject metaObject = configuration.newMetaObject(parameterObject);
                    value = metaObject.getValue(propertyName);
                    // 省略部分代码
                }
            }
        }
    }
}
```

对于 `MetaObject` 来讲，一般获取对应的参数属性是通过 `MyBatis` 内置的反射工具类存储对应的方法，在获取时通过 Getter 规则匹配对应的方法，最后通过反射的方式调用对应的 Getter 方法获取属性。其中，缓存 Getter 方法对应的源代码如下：

``` java
public class Reflector {
    public Reflector(Class<?> clazz) {
        Method[] classMethods = getClassMethods(clazz);
        addGetMethods(classMethods);
        // 省略部分代码
    }

    // 通过属性名获取对应的 Getter 方法，这里的 Invoker 是对 Method 的封装
    public Invoker getGetInvoker(String propertyName) {
        Invoker method = getMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }
}
```

为了能够使得方法名能够对应特定的属性，我们需要自己进行相关参数值的获取，结合上文提到的对于 Getter 方法的处理，我们可以从这个角度入手，替换掉获取属性值的默认行为。为了简化问题，我们只需要拓展需要的部分即可，因此我们定义自己的 `Reflector`：

``` java
import org.apache.ibatis.reflection.Reflector;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author xhliu
 */
public class TaskInfoReflector
    extends Reflector {

    private final static Map<String, Method> map = new TreeMap<>();

    public static interface TaskInfo {
        long userId();
        String userName();
    }

    static {
        /*
        	使用合适的反射工具获取对应类型的所有方法即可
        */
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(TaskInfo.class);
        for (Method m : methods) {
            map.put(m.getName(), m);
        }
    }

    public TaskInfoReflector(Class<?> clazz) {
        super(clazz);
    }

    public Invoker getGetInvoker(String propName) {
        /*
        	这里我们将属性名直接和方法名进行匹配，如果能够匹配则直接封装对应的 Method 作为 Invoker 返回给客户端
        */
        for (Map.Entry<String, Method> entry : map.entrySet()) {
            String name = entry.getKey();
            Method method = entry.getValue();
            int ps = method.getParameterCount();
            if (ps > 0) continue;
            if (propName.equals(name) && (String.class == method.getReturnType()
                                          || method.getReturnType().isPrimitive())) {
                return new MethodInvoker(method);
            }
        }
        /*
        	对于不能处理的属性名，委托给父类进行原有逻辑的处理，这也是装饰器模式的常见使用方式
        */
        return super.getGetInvoker(propName);
    }
}
```

为了使得我们自定义的反射工具能够在 MyBatis 中使用，我们需要将其注册到 MyBatis 中，以替换原有的内置反射工具。结合对应的源码可知，MyBatis 中获取 `Reflector` 是通过工厂方法模式来实现的，在 `Configuration` 类中持有对应的 `ReflectorFactory`，以此来创建对应的 `Reflector` 

因此我们需要定义自己的 `ReflectorFactory`，时期能够替换默认的 `ReflectorFactory`，对应代码如下：

``` java
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.Reflector;

/**
 * @author xhliu
 */
public class TaskInfoReflectorFactory
        extends DefaultReflectorFactory {

    public Reflector findForClass(Class<?> type) {
        if (!(TaskInfo.class.isAssignableFrom(type))) {
            return super.findForClass(type);
        }
        return new TaskInfoReflector(type);
    }
}
```

再结合 `Configuration` 配置类的解析过程，对于 `XML` 格式的配置，我们可以直接指定这个属性：

``` xml
<?xml version="1.0" encoding="utf-8" ?>
<!DOCTYPE configuration
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-config.dtd" >
<configuration>
    <reflectorFactory type="com.example.demo.reflect.TaskInfoReflectorFactory"/>
    <!-- 省略部分其它配置属性 -->
</configuration>
```

而对于整合到 Spring 的 MyBatis 来讲，情况要稍微复杂一点。MyBatis 在 Spring 中进行配置是通过 `org.mybatis.spring.boot.autoconfigure.MybatisProperties`  完成的，因此如果我们希望配置 `Configuration` 中的相关属性，那么可以在 Spring 的配置文件（以 `yaml` 文件为例）做对应配置：

``` yaml
mybatis:
  configuration:
    cache-enabled: true
    local-cache-scope: statement
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    reflectorFactory: com.example.demo.reflect.TaskInfoReflectorFactory # 自定义反射工厂的全限定名
```

然而这样配置无法生效，甚至会得到类似以下的异常：

``` text
Failed to bind properties under 'mybatis.configuration.reflector-factory' to org.apache.ibatis.reflection.ReflectorFactory:

    Property: mybatis.configuration.reflector-factory
    Value: "com.example.demo.reflect.TaskInfoReflectorFactory"
    Origin: class path resource [application.yml] - 22:23
    Reason: org.springframework.core.convert.ConverterNotFoundException: No converter found capable of converting from type [java.lang.String] to type [org.apache.ibatis.reflection.ReflectorFactory]
```

这是因为 Spring 的配置属性为字符串类型，而我们需要的是实际的对象，并且在 Spring 中不存在这样的转换关系，因此出现了这样的问题

为了解决这个问题，我们需要自定义转换类，实现类型名称到实际对象的转换：

``` java
import org.apache.ibatis.reflection.ReflectorFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;

/**
 * @author xhliu
 */
@Component
@ConfigurationPropertiesBinding
public class StringToReflectFactoryConvert
        implements Converter<String, ReflectorFactory> {

    public ReflectorFactory convert(@Nonnull String source) {
        try {
            Class<?> clazz = Class.forName(source);
            if (!ReflectorFactory.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("非法的反射工厂类型: " + clazz.getName());
            }
            // 这里假定反射工厂类都存在默认的无参构造函数
            Constructor<?> constructor = clazz.getConstructor();
            return (ReflectorFactory) constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

这样配置后，由于 Spring 会扫描 `@Component` 注解的类作为 `Bean` 注册到容器中，同时会将其放入到转换链，因此最终能够实现 String —> `ReflectorFactory` 的转换

值得一提的是，在配置转换类时，如果使用 `@Configuration` 替换 `@Component` 用于标记 `Bean`，可能同样会出现找不到转换类的问题，这是因为 `@Configuration` 的目的是为了标识配置组件，Spring 默认会对其创建对应的代理对象，这会导致 Spring 无法检测到 `@ConfigurationPropertiesBinding` 注解，使得配置的转换类失效。关于 Spring 对于 `@Configuration` 的特殊处理，可以查看：https://www.cnblogs.com/daihang2366/p/15125874.html

如果要求必须使用 `@Configuration`，那么必须设置为 `@Configuration(proxyBeanMethods = false)` 来禁用默认的代理行为

