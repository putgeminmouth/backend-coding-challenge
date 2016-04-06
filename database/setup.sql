DROP TABLE IF EXISTS geoname;
CREATE TABLE geoname(
    id         INTEGER,
    name       VARCHAR(200),
    ascii      VARCHAR(200),
    normalized VARCHAR(200),
    latitude   DECIMAL,
    longitude  DECIMAL,
    admin1     VARCHAR(20),
    admin2     VARCHAR(20),
    admin3     VARCHAR(20),
    admin4     VARCHAR(20),

    PRIMARY KEY(id)
);
