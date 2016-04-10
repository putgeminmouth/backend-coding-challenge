# Tardar Sauce
![Tardar Sauce](http://cdn.grumpycats.com/wp-content/uploads/2016/02/12654647_974282002607537_7798179861389974677_n-758x758.jpg)

https://tardar-sauce.herokuapp.com/

### Requires
* sbt
* heroku
* postgres

### Configuration
| key | description |
| --- | --- |
| `dao.hardlimit` | limit number of results returned, overrides any values in the request |
| `dao.impl` | one of: dao.PrefixPostgresSuggestionDao, dao.SimilarityPostgresSuggestionDao, see below |

In all cases, user and data input are matched against a normalized value which ignores case and accents (e.g. Montréal -> montreal)

#### PrefixPostgresSuggestionDao
Matches names based on prefixes.

Score simply reflects the ordering of the results and can only be used to compare against other scores in the same result for a given request.

#### SimilarityPostgresSuggestionDao
Matches names based on trigrams (http://www.postgresql.org/docs/9.1/static/pgtrgm.html). When coordinates are also given, the scores for name and distance are combined according to the weights specified.

Distance scale is a number by which the distance between given coordinates and cities are divided. It serves to normalize distance into the range [0,1] before applying the weight factor. As such it effectively also defines the max distance that can be searched against (after which any coordinates specified will score as 0).

Score can be compared across requests (**for a given set of configuration values**) and the values can be meaningfully used, e.g. assigning a color based on score value.

**Important** `similarity.nameWeight` and `similarity.distanceWeight` must add up to `1`.

**dao-specific configuration**
| key | description |
| --- | --- |
| `similarity.nameWeight` | [0,1] importance given to the name |
| `similarity.distanceWeight` | [0,1] importance given to the distance |
| `similarity.distanceScale` | (0,∞) cut-off, in meters, for distance |


### Database
See also: 
* https://devcenter.heroku.com/articles/heroku-postgresql
* https://devcenter.heroku.com/articles/heroku-local

You will need to setup an env var `JDBC_DATABASE_URL`.

***local database*** `postgresql://tardar:sauce@localhost:5432/tardar`

***heroku database*** `heroku run echo \$JDBC_DATABASE_URL`

#### DDL
run `database/setup.sql` from the database client of your choice.
#### Data Import
class `scripts.db.DBImport` reads the files in `data/` and populates the database.

##### Local
**shell** `./target/universal/stage/bin/tardar-sauce -m scripts.db.DBImport`

**sbt** `runMain scripts.db.DBImport`

##### Heroku Remote
**shell** `heroku run ./target/universal/stage/bin/tardar-sauce -m scripts.db.DBImport`

### How to run
##### Local
**sbt** (default port 9000)
```
sbt
[Tardar Sauce] $ run
```
**heroku local*** (default port 5000)
```
heroku local
```
