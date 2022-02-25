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

CREATE TABLE IF NOT EXISTS friend
(
    self_id   VARCHAR(20) NOT NULL,
    friend_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (self_id, friend_id),
    CONSTRAINT self_id_foreign FOREIGN KEY (self_id) REFERENCES vk_user (id),
    CONSTRAINT friend_id_foreign FOREIGN KEY (friend_id) REFERENCES vk_user (id)
);

/* Crete rule to avoid repeat insert data */
CREATE RULE vk_user_insert_ignore AS ON INSERT TO vk_user WHERE EXISTS(
        SELECT 1
        FROM vk_user
        WHERE id = new.id
    ) DO INSTEAD NOTHING;
CREATE RULE friend_insert_ignore AS ON INSERT TO friend WHERE EXISTS(
        SELECT 1
        FROM friend
        WHERE self_id = new.self_id
          AND friend_id = new.friend_id
    )
    DO INSTEAD NOTHING;

-- 数据插入部分
insert into public.vk_user (id, first_name, last_name, is_closed, can_access_close, domain, online, track_code)
values  ('1', 'Xianghai', 'Liu', false, true, 'www.google.com', 13564, '7c4a8d09ca3762af61e59520943dc26494f8941b'),
        ('2', 'Yongfeng', 'Zhao', false, true, 'www.vk.vom', 26497, 'df6c025064f6cfca940c8b24c212f226e06d1ce7'),
        ('3', 'Jian', 'Du', false, true, 'www.google.com', 13246, '570d931f9e3a5b3315081cbdbffa375bbc3732b0'),
        ('4', 'Gang', 'Xu', false, true, 'www.baidu.com', 15674, '479ce1e3f7d2c2f067fbc41132d489276f511c3c'),
        ('5', 'Yulong', 'Guo', true, false, 'www.vk.com', 56794, 'cb17d8ce007c1e12aa8c6facf27f3802c20085a9'),
        ('6', 'Zhiping', 'Deng', true, true, 'www.google.com', 13546, 'ddd2161b25f5e83b457ac416435bd2a9b0cd319c'),
        ('7', 'Yongjian', 'Chang', true, false, 'www.baidu.com', 79843, '278f8ea5e2c88aa508eed086d7dd819d89c10fae'),
        ('8', 'Hao', 'Zhou', false, false, 'www.vk.vom', 15434, '9f9c58540ed85334688e8cd46254e953e71e6845'),
        ('9', 'Xiaohan', 'Chen', true, true, 'www.google.com', 16798, '60cd5914aa6c63d0c17133f0b3bfd28caab3193d'),
        ('10', 'Zixuan', 'Liu', true, false, 'www.baidu.com', 16574, '580e58f8918e7da55445c28247300476dc16a10b');
insert into public.friend (self_id, friend_id)
values  ('1', '2'),
        ('1', '3'),
        ('1', '6'),
        ('1', '7'),
        ('1', '9'),
        ('10', '9'),
        ('2', '3'),
        ('2', '4'),
        ('2', '5'),
        ('2', '6'),
        ('2', '7'),
        ('2', '8'),
        ('2', '9'),
        ('2', '1'),
        ('3', '2'),
        ('3', '4'),
        ('3', '5'),
        ('3', '1'),
        ('4', '3'),
        ('4', '5'),
        ('4', '8'),
        ('4', '2'),
        ('5', '2'),
        ('5', '3'),
        ('5', '4'),
        ('6', '7'),
        ('6', '8'),
        ('6', '1'),
        ('6', '2'),
        ('7', '1'),
        ('7', '2'),
        ('7', '6'),
        ('8', '4'),
        ('8', '2'),
        ('9', '1'),
        ('9', '2'),
        ('9', '10');
-- 数据插入结束

/**
 * drop exist friend_num and relation table
 * This is use this two table to save every user friend num
 */
DROP TABLE IF EXISTS friend_num;
DROP TABLE IF EXISTS relation;
CREATE TABLE IF NOT EXISTS friend_num
(
    id   VARCHAR(20) NOT NULL UNIQUE PRIMARY KEY,
    rate FLOAT,
    CONSTRAINT id_foreign FOREIGN KEY (id) REFERENCES vk_user (id)
);

CREATE TABLE IF NOT EXISTS relation
(
    self_id    VARCHAR(20) NOT NULL,
    fri_id     VARCHAR(20) NOT NULL,
    self_first TEXT,
    self_last  TEXT,
    fri_first  TEXT,
    fri_last   TEXT
);

WITH temp AS (
    SELECT id AS self_id, friend.friend_id AS fri_id, first_name, last_name, friend_id
    FROM VK_user,
         friend
    WHERE VK_user.id = friend.self_id
      AND first_name = 'Xianghai'
)
INSERT
INTO relation
SELECT self_id,
       fri_id,
       temp.first_name    AS self_first,
       temp.last_name     AS self_last,
       VK_user.first_name AS fri_first,
       VK_user.last_name  AS fri_last
FROM temp
         JOIN VK_user ON fri_id = VK_user.id;

INSERT INTO friend_num
SELECT relation.self_id,
       round(1::numeric / count(relation.fri_id)::numeric, 4) AS friend_num
FROM relation
GROUP BY self_id;

DO
$$
    DECLARE
        re varchar(20);
    BEGIN
        FOR re IN SELECT relation.fri_id FROM relation
            LOOP
                INSERT INTO friend_num
                SELECT re AS id, round(1::numeric / count(re)::numeric, 4) AS friend_num
                FROM friend
                WHERE self_id = re;
            end loop;
    END
$$;

CREATE TABLE friend_rank
(
    id   VARCHAR(20) PRIMARY KEY NOT NULL UNIQUE,
    rank FLOAT,
    CONSTRAINT rank_id_foreign FOREIGN KEY (id) REFERENCES friend_num (id)
);
CREATE OR REPLACE FUNCTION PageRank() RETURNS VOID AS
$$
DECLARE
    conversionFactor FLOAT := 0.85;
    DECLARE ratio    FLOAT;
    DECLARE rank     FLOAT;
    DECLARE nodeNum  INT;
    DECLARE MainId   VARCHAR(20);
    DECLARE ObjectId VARCHAR(20);
BEGIN
    DELETE FROM friend_rank WHERE TRUE;
    SELECT count(friend_num.id) FROM friend_num INTO nodeNum;
    FOR MainId IN SELECT friend_num.id FROM friend_num
        LOOP
            rank := 0.0;
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
SELECT PageRank();
SELECT * FROM vk_user JOIN friend_rank ON vk_user.id = friend_rank.id ORDER BY rank DESC;
