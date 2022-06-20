# Java 中时间对象的序列化

在 Java 应用程序中，时间对象是使用地比较频繁的对象，比如，记录某一条数据的修改时间，用户的登录时间等应用场景。在传统的 Java 编程中，大部分的程序员都会选择使用 `java.uti.Date` 这个类型的类来表示时间（这个类可不是什么善类）。

在现代化互联网的使用场景中，由于前后端分离的原因，在前后端之间进行数据的交互都会默认采用 `JSON`（JavaScript Object Notion 即 JavaScript 对象表示法）来完成前后端的数据交互。在对时间对象的 `JSON` 序列化处理的过程中，可能或多或少都会遇到一些坑，本文将结合笔者自身遇到的一些问题，提供我个人认为比较合理的解决方案。

## `Date` 对象的序列化

如果你正在使用 `java.util.Date` 或者它的一些子类，那么请尽快放弃使用这一系列类，这个类可能是 `Java` 中为数不多令人觉得恶心的类。这个类存在以下几点显而易见的缺陷：

- 这个类是表示时间的，但是它表示的时间并没有时区的概念，只是单纯地存储了一个 `long` 类型的时间戳来表示时间，而这个时间戳则是基于系统默认的时区

- 尽管大部分的 `getter` 和 `setter` 方法已经被弃用了，但是如果去翻看这个类的 `getYear()` 等方法绝对会让你大吃一惊。它的 `year` 是基于 `1900` 年为起始年，`month` 则是以 $0$ 为开始月份

- 这个类是一个可变类，这意味着在记录了一个时间之后，依旧可以修改这个时间对象，这从设计上来讲是不合理的

尽管自 JDK 1.1 开始着手设计了 `java.util.Calendar` 准备修复这个类存在的一些问题，但是结果不是很明显，`java.util.Calendar` 依旧是可变的

**出于以上的一些原因，建议不要使用 `java.util` 包下的时间类**，如果必要，可以考虑使用 `java.time.Instant` 来替换 `java.util.Date`

但是总会有意外，如果现有的系统中存在大量的使用 `java.util.Date` 的场景，那么也只能试着和这个类友好的相处。对于没有配置任何序列化规则的 `JSON` 序列化工具类，会默认将类中的所有实例属性递归地进行处理方法来转换成对应的 `JSON` 内容。对于下面定义的类：

```java
import java.util.Date;

public class Person {
    private String name;
    private Date createdTime;

    // 省略部分 Getter 和 Setter 方法
}
```

使用下面的方法来设置相关的属性：

```java
Person person = new Person();
person.setName("xhliu");
person.setCreatedTime(new Date());
```

### Jackson 的序列化

当使用 Jackson 将这个对象进行序列化时（此时没有设置 Jackson ），会得到类似下面的输出结果：

```json
{"name":"xhliu","createdTime":1655642583437}
```

由于 `Date` 默认情况下只有一个存储时间戳的非空属性，因此会将其进行序列化。显然，实际使用时肯定不希望是这样的格式。如果希望 Jackson 能够序列化成指定的格式，可以在这个 `Date` 类型的属性上加上 `@JsonFormat` 注解使得 Jackson 序列化成对应的格式，一般都会采用如下的格式：

```java
import com.fasterxml.jackson.annotation.JsonFormat;
class Person {
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;
}
```

Jackson 中 `@JsonFormat` 注解的目的是格式化属性的序列化形式，在这里格式化 `Date` 的输出格式，具体的 `Date` 的格式以及各个字段的含义可以参考：<a href="[ISO 8601 - Wikipedia](https://en.wikipedia.org/wiki/ISO_8601)">ISO 8601</a>

此时，再使用 Jackson 进行序列化可以看到类似下面的效果：

```json
{"name":"xhliu","createdTime":"2022-06-19 13:08:51"}
```

除了在预先的字段上加上 `@JsonFormt` 的注解来显式地格式化时间，也可以通过配置 Jackson 的全局日期格式来配置日期的输出格式，如下所示：

```java
ObjectMapper mapper = new ObjectMapper();
mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
```

需要注意的是，`@JsonFormat` 的优先级会高于全局的配置，因此，如果遇到某些需要进行特定格式化的场景，使用 `@JsonFormat` 是一个比较好的选择

### Jackson 的反序列化

由于已经使用了相关的序列化格式，因此在进行反序列化时也需要按照相同的格式才能完成`JSON` 的反解析。大部分的 REST 请求在接受参数时，对于 `Date` 的解析出现异常都是由于格式不匹配导致的，为了解决这个问题，可以使用 `@JsonFormat 的注解来规定时间的格式，使得它能够正常解析对应的时间属性`

### Gson 的序列化

和 Jackson 的序列化不同，Gson 在没有配置相关的属性的情况下，会调用 `Date` 的 `toString` 方法来填充属性的 `JSON` 值，对于上面的例子，如果使用没有进行任何配置的 Gson 来进行 `JSON` 的序列化，输出的结果可能如下所示：

```json
{"name":"xhliu","createdTime":"Jun 19, 2022, 9:20:41 PM"}
```

和默认的 Jackson 的输出相比，只能说是一个五八，一个四十了

如果想要格式化 `Date`，需要为 Gson 注册一个序列化适配器，注册到 Gson 中来实现 `Date` 的序列化。可以手动实现序列化适配器，只需要定义一个类实现相关的序列化和反序列化操作即可，类似的适配器如下所示：

```java
import com.google.gson.*;
import lombok.SneakyThrows;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NormalDateSerializerAdapter 
    implements JsonSerializer<Date>, JsonDeserializer<Date> {
    @Override
    public JsonElement serialize(Date src, Type typeOfSrc, JsonSerializationContext context) {
        /*
         * 尽管 SimpleDateFormat 是线程不安全的（网上铺天盖地都有的八股文），但是在使用的过程中
         * 中是一个 ”栈封闭“ 的状态，明显这项操作是线程安全的
         *
         * Tips: SimpleDateFormat 不是是线程安全的类，因为它在格式化日期的过程中修改了私有属性状态，
         *       并且没有使用任何同步手段来保证操作的有序性
         */
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return new JsonPrimitive(format.format(src));
    }
    
    @SneakyThrows
    @Override
    public Date deserialize(
            JsonElement json, Type typeOfT,
            JsonDeserializationContext context
    ) throws JsonParseException {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.parse(json.getAsString());
    }
}
```

在 Gson 的构造过程中注入这个类型适配器：

```java
Gson gson = new GsonBuilder()
                .registerTypeAdapter(Date.class, new NormalDateSerializerAdapter())
                .create();
```

此时再进行序列化的操作，可以看到 `Date` 已经符合一般的表现形式了：

```json
{"name":"xhliu","createdTime":"2022-06-20 09:31:43"}
```

有时由于恶性的需求的原因，这种全局的时间格式可能并不能满足要求。有些需求并不需要时间精确到时分秒。针对这种情况，在 Gson 中注册类型适配器无法满足要求。和 Jackson 类似同，Gson 也支持通过注解的方式来设置字段的序列化格式，这样就能够使得序列化的领域精确到某一个字段属性，Gson 通过 `@JsonAdapter` 的注解来定义属性的对象序列化格式，如下所示：


```java
import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import lombok.SneakyThrows;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Person {
    private String name;

    @JsonAdapter(value = YearDateSerializerAdapter.class)
    private Date createdTime;

    // 自定义的日期序列化格式
    public static class YearDateSerializerAdapter
            implements JsonDeserializer<Date>, JsonSerializer<Date> {

        @Override
        public JsonElement serialize(Date src, Type typeOfSrc, JsonSerializationContext context) {
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            return new JsonPrimitive(format.format(src));
        }

        @SneakyThrows
        @Override
        public Date deserialize(
                JsonElement json, Type typeOfT,
                JsonDeserializationContext context
        ) throws JsonParseException {
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            return format.parse(json.getAsString());
        }
    }
}


```

这样，在序列化这个对象时将会按照 `@JsonAdapter`  注解中定义的类型适配器进行序列化和反序列化

### Gson 的反序列化

由于已经配置了相关的类型适配器，因此反序列化时需要符合预期能够解析的格式。对于上面的适配器来讲，只要是符合 "yyy-MM-dd HH:mm:ss" 的格式便能够进行解析。具体处理时需要注意和自己的反序列化格式相匹配

## `java.time` 包下时间对象的序列化

`java.time` 包下的的时间类自 JDK 1.8 引入，它解决了原有 `java.util` 包下有关时间类中存在的问题。这些类相比较于旧有的时间类，存在以下的优势：

- API 都是很清楚的、易懂的，和 `Date` 的 `get` 系列方法形成鲜明对比

- 和 `Date` 直接保存时间戳不同，`java.time` 包下表示时间的类是可伸缩的，比如：`Instant` 就表示时间戳、`LocalDate` 表示日期（年、月、日）、`ZonedDateTime` 表示带有时区的时间

- `java.time` 包下的所有类都是不可变类，这也就意味着它们一定是线程安全的

- 这个包下的类提供了链式的 API 调用，使得代码意图更加清晰

### Jackson 的序列化

对于 `java.time` 包下的时间类，Jackson 已经提供了相应的时间模块来处理这些类的序列化和反序列化操作。如果




