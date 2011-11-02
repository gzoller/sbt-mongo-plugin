package co.nubilus

import java.io.File
import sbt._
import Keys._
import Process._

object SbtMongoPlugin extends Plugin
{
	val mongoTask = TaskKey[Unit]("mongo-task","Load fixture data into mongo")
	val mongoFixtureDirectory = SettingKey[File]("mongo-fixture-directory","Directory where mongo fixtures will be found")
	val mongoMigrationDirectory = SettingKey[File]("mongo-migration-directory","Directory where mongo migration scripts will be found")

	lazy val mongoSettings:Seq[Project.Setting[_]] = Seq(
		mongoFixtureDirectory <<= (unmanagedResourceDirectories in Test).apply({ (f) => new java.io.File(f.head, "/fixtures") }),
		mongoMigrationDirectory <<= mongoFixtureDirectory,
		mongoTask <<= (mongoFixtureDirectory, mongoMigrationDirectory) map { 
			(fixDir, migDir) => {
				// Initial Data Load
				lazy val mongoFixturePaths = fixDir ** "*.json" // find all json files in fixtures directory
				mongoFixturePaths.getPaths.foreach { p =>
					val (dbname, cn) = mongoCollectionName((new File(p)).getName)  // parse out db and collection names
					val path = { if( p.contains(" ") ) '"'+p+'"' else p } // hack for Windows...may have space in path, which tanks mongoimport unless you quote the path
					Process("mongoimport -d %s -c %s --file %s --drop".format(dbname,cn,path), None, ("path",System.getenv.get("Path")))!
				}
				
				// Migrations
				lazy val mongoMigrationPaths = migDir ** "*.migration.js"
				mongoMigrationPaths.getPaths.foreach { p =>
					val dbname = mongoMigrationName((new File(p)).getName)
					val path = { if( p.contains(" ") ) '"'+p+'"' else p } // hack for Windows...may have space in path, which tanks mongoimport unless you quote the path
					Process("mongo %s %s".format(dbname,path), None, ("path",System.getenv.get("Path")))!
				}
			}
		}
	)
	
	private val FixtureFile = """([^.]*)\.([^.]*)\.json""".r
	def mongoCollectionName(basename : String) : (String, String) = basename match {
		case FixtureFile(dbname, name) => (dbname, name)
	}
	private val MigrationFile = """([^.]*)\.migration\.js""".r
	def mongoMigrationName(basename : String) : (String) = basename match {
		case MigrationFile(dbname) => (dbname)
	}
}