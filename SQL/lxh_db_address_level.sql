CREATE TABLE address_level
(
    id   INT NOT NULL PRIMARY KEY,
    name VARCHAR(32),
    p_id INT
);

INSERT INTO address_level (id, name, p_id) VALUES (1, '太阳系', null);
INSERT INTO address_level (id, name, p_id) VALUES (2, '地球', 1);
INSERT INTO address_level (id, name, p_id) VALUES (3, '中国', 2);
INSERT INTO address_level (id, name, p_id) VALUES (4, 'xx省', 3);
INSERT INTO address_level (id, name, p_id) VALUES (5, 'xx市', 4);
INSERT INTO address_level (id, name, p_id) VALUES (6, 'xx县', 5);
INSERT INTO address_level (id, name, p_id) VALUES (7, 'xx乡', 6);
INSERT INTO address_level (id, name, p_id) VALUES (8, 'xx村', 7);
