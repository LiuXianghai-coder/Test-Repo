# 使用 PostgreSQL 实现 PageRank

### PageRank 算法

​	作为 Google 最早的一个网页排名算法，该算法在早期的搜索引擎中是搜索结果最为准确的，同时也是 Google 发家的一个重要算法。尽管这些年来该算法不再是 Google 对于网页排名的唯一算法，但是它的核心思想还是值得我们去研究一下的。

​	算法简单描述：首先假定每个网页被引用的概率是相同的，然后通过计算每个网页被其它网页链接的权值进行进一步的概率计算，得到每个页面被引用的概率，再乘上对应的修正因子以及加上最小的概率，最后按照这个概率进行排序。

​	简化的计算公式如下所示：
$$
PR(p_i) = \frac{1 - d}{N} + d\sum\limits_{p_j\in M(p_i)} \frac{PR(p_j)}{L(p_j)}
$$
​	其中 PR(pi) 表示 pi 网页被引用的概率；d 表示阻尼系数，表示任意时刻yong'hu访问到某一网页之后访问下一页面的概率；N 表示总的网页个数；L 表示 pj 所链接的网页总数；M 表示 pi 链接的集合。

### PLPGSQL

​	PLPGSQL 是 PostgreSQL 的一个可加载的过程语言，通过 PLPGSQL 可以用于：创建函数和触发器、执行一般程序语言的控制语句、定义数据变量等一般程序设计语言的能做的事。因此，使用 PostgreSQL 实现 PageRank 再理论上是可行的。

### 实现

​	这里的实现的目标是通过人际之间的关系，将集合内的人按照威望的高度从高到低排序。这里的威望只是单纯地计算他与其它人的联系数量得出的。按照 PageRank 的思想，可以通过 PageRank 完成这个任务。

- 首先创建数据表

  ```sql
  -- 用户数据表，包含一些基本的数据，在本次实现中实际主要用到的只有 ID
  CREATE TABLE IF NOT EXISTS vk_user
  (
      id               VARCHAR(20) NOT NULL UNIQUE PRIMARY KEY,
      first_name       TEXT,
      last_name        TEXT,
      is_closed        BOOLEAN,
      can_access_close BOOLEAN,
      domain           TEXT,
      online           INT,
      track_code       TEXT
  );
  
  -- 这些用户之间的关联关系表
  CREATE TABLE IF NOT EXISTS friend
  (
      self_id   VARCHAR(20) NOT NULL,
      friend_id VARCHAR(20) NOT NULL,
      PRIMARY KEY (self_id, friend_id),
      CONSTRAINT self_id_foreign FOREIGN KEY (self_id) REFERENCES vk_user (id),
      CONSTRAINT friend_id_foreign FOREIGN KEY (friend_id) REFERENCES vk_user (id)
  );
  
  -- 每个用户的朋友信息情况表，这里的 rate 就相当于上文公式内的 1/L(pj)
  CREATE TABLE IF NOT EXISTS friend_num
  (
      id   VARCHAR(20) NOT NULL UNIQUE PRIMARY KEY,
      rate FLOAT,
      CONSTRAINT id_foreign FOREIGN KEY (id) REFERENCES vk_user (id)
  );
  
  -- 用于保留最终结果的数据表，类似于得到的搜索结果向量
  CREATE TABLE friend_rank
  (
      id   VARCHAR(20) PRIMARY KEY NOT NULL UNIQUE,
      rank FLOAT,
      CONSTRAINT rank_id_foreign FOREIGN KEY (id) REFERENCES friend_num (id)
  );
  ```

- 插入数据

  ```sql
  -- 数据插入部分，这部分数据是来源自己的生活大致得到的
  INSERT INTO vk_user (id, first_name, last_name, is_closed, can_access_close, domain, online, track_code)
  VALUES  ('1', 'Xianghai', 'Liu', false, true, 'www.google.com', 13564, '7c4a8d09ca3762af61e59520943dc26494f8941b'),
          ('2', 'Yongfeng', 'Zhao', false, true, 'www.vk.vom', 26497, 'df6c025064f6cfca940c8b24c212f226e06d1ce7'),
          ('3', 'Jian', 'Du', false, true, 'www.google.com', 13246, '570d931f9e3a5b3315081cbdbffa375bbc3732b0'),
          ('4', 'Gang', 'Xu', false, true, 'www.baidu.com', 15674, '479ce1e3f7d2c2f067fbc41132d489276f511c3c'),
          ('5', 'Yulong', 'Guo', true, false, 'www.vk.com', 56794, 'cb17d8ce007c1e12aa8c6facf27f3802c20085a9'),
          ('6', 'Zhiping', 'Deng', true, true, 'www.google.com', 13546, 'ddd2161b25f5e83b457ac416435bd2a9b0cd319c'),
          ('7', 'Yongjian', 'Chang', true, false, 'www.baidu.com', 79843, '278f8ea5e2c88aa508eed086d7dd819d89c10fae'),
          ('8', 'Hao', 'Zhou', false, false, 'www.vk.vom', 15434, '9f9c58540ed85334688e8cd46254e953e71e6845'),
          ('9', 'Xiaohan', 'Chen', true, true, 'www.google.com', 16798, '60cd5914aa6c63d0c17133f0b3bfd28caab3193d'),
          ('10', 'Zixuan', 'Liu', true, false, 'www.baidu.com', 16574, '580e58f8918e7da55445c28247300476dc16a10b');
  INSERT INTO friend (self_id, friend_id)
  VALUES  ('1', '2'),('1', '3'),('1', '6'),('1', '7'),('1', '9'),('10', '9'),('2', '3'),('2', '4'),
          ('2', '5'),('2', '6'),('2', '7'),('2', '8'),('2', '9'),('2', '1'),('3', '2'),('3', '4'),
          ('3', '5'),('3', '1'),('4', '3'),('4', '5'),('4', '8'),('4', '2'),('5', '2'),('5', '3'),
          ('5', '4'),('6', '7'),('6', '8'),('6', '1'),('6', '2'),('7', '1'),('7', '2'),('7', '6'),
          ('8', '4'),('8', '2'),('9', '1'),('9', '2'),('9', '10');
  -- 数据插入结束
  
  -- 根据上文的信息得到 friend_num 的数据
  INSERT INTO friend_num
  SELECT friend.self_id,
         round(1::numeric / count(friend.friend_id)::numeric, 4) AS friend_num
  FROM friend
  GROUP BY self_id;
  ```

- 计算函数创建

  ```sql
  CREATE OR REPLACE FUNCTION PageRank() RETURNS VOID AS
  $$
  DECLARE
      -- 阻尼系数
      conversionFactor FLOAT := 0.85;
      DECLARE ratio    FLOAT;
      DECLARE rank     FLOAT;
      DECLARE nodeNum  INT;
      DECLARE MainId   VARCHAR(20);
      DECLARE ObjectId VARCHAR(20);
  BEGIN
      -- 每次执行时，都要删除原有记录，因为结果是通过插入的方式得到的
      DELETE FROM friend_rank WHERE TRUE;
      -- 得到整个集合的节点数，对应上文公式中的 N
      SELECT count(friend_num.id) FROM friend_num INTO nodeNum;
      -- 遍历每个节点，得到对应的概率
      FOR MainId IN SELECT friend_num.id FROM friend_num
          LOOP
              rank := 0.0;
              -- 遍历每个非自生节点，得到其它节点对当前节点的权重概率贡献并累加
              FOR ObjectId IN SELECT friend_num.id FROM friend_num
                  LOOP
                      IF MainId = ObjectId OR ObjectId NOT IN (SELECT friend_id FROM friend WHERE self_id = MainId) THEN
                          rank := rank + 0.0;
                      ELSE
                          SELECT friend_num.rate FROM friend_num WHERE id = ObjectId INTO ratio;
                          rank := rank + ratio * round(1::numeric / nodeNum::numeric, 4) * conversionFactor +
                                  round((1 - conversionFactor)::numeric / nodeNum::numeric, 4);
                      end if;
                  end loop;
              INSERT INTO friend_rank VALUES (MainId, rank);
          end loop;
  END;
  $$ LANGUAGE plpgsql;
  ```

- 执行查询

  ```sql
  -- 首先，调用 PageRank 函数更新结果向量
  SELECT PageRank();
  
  -- 连接用户表，得到相关的排名信息
  SELECT vk_user.id, first_name, last_name, friend_rank.rank FROM vk_user JOIN friend_rank ON vk_user.id = friend_rank.id ORDER BY rank DESC;
  ```

  ​	最终得到如下查询结果：
  <img src="https://s3.jpg.cm/2021/08/11/IXScr4.png">

  ​	

  ​	与日常生活的情况相结合，结合实际情况，确实是这个人更加 ”权威“ 一些。由此可见，PageRank 的效果还是相当不错的。

  

  ​	如果你也有自己的交际圈，你也可以用这个算法试一试，没准能带给你一些不一样的体验！



