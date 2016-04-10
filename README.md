# Tardar Sauce
![Tardar Sauce](http://cdn.grumpycats.com/wp-content/uploads/2016/02/12654647_974282002607537_7798179861389974677_n-758x758.jpg)

https://tardar-sauce.herokuapp.com/

## Dev Environment

Requires
* sbt
* heroku
* postgres

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
