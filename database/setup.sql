create extension cube;
create extension earthdistance;
create extension pg_trgm;

DROP TABLE IF EXISTS geoname;
CREATE TABLE geoname(
    id         INTEGER,
    name       VARCHAR(200),
    normalized VARCHAR(200),
    latitude   DECIMAL,
    longitude  DECIMAL,
    country    VARCHAR(200), -- assumption on length, not in spec
    division   VARCHAR(200), -- assumption on length, not in spec

    PRIMARY KEY(id)
);
