package co.nubilus

import java.io.File
import sbt._
import Keys._

object SbtMongoPlugin extends Plugin
{
	val mongoTask 				= TaskKey[Unit]   ("mongo-load", "Load fixture data into mongo")
	
	def mongo = 
		(unmanagedResourceDirectories in mongoTask) map {
			(baseMongoDir) => {
				val mongoDir = new java.io.File(baseMongoDir.head,"/mongo")

				// Initial Data Load
				lazy val mongoFixturePaths = mongoDir ** "*.json" // find all json files in fixtures directory
				mongoFixturePaths.getPaths.foreach { p =>
					val (dbname, cn) = mongoCollectionName((new File(p)).getName)  // parse out db and collection names
					val path = { if( p.contains(" ") ) '"'+p+'"' else p } // hack for Windows...may have space in path, which tanks mongoimport unless you quote the path
					Process("mongoimport -d %s -c %s --file %s --drop".format(dbname,cn,path), None, ("path",System.getenv.get("Path")))!
				}
				
				// Migrations
				lazy val mongoMigrationPaths = mongoDir ** "*.migration.js"
				mongoMigrationPaths.getPaths.foreach { p =>
					val dbname = mongoMigrationName((new File(p)).getName)
					val path = { if( p.contains(" ") ) '"'+p+'"' else p } // hack for Windows...may have space in path, which tanks mongoimport unless you quote the path
					Process("mongo %s %s".format(dbname,path), None, ("path",System.getenv.get("Path")))!
				}
			}
		}
	def mongoSettingsIn(conf:Configuration) = 
		inConfig(conf)(Seq(
			unmanagedResourceDirectories in mongoTask <<= (unmanagedResourceDirectories in conf),
			mongoTask <<= mongo
		))
	// This bit of magic makes my setting config-relative.  So in this instance unmanageResourceDirectories is
	// set based on config.
	def mongoSettings = mongoSettingsIn(Compile) ++	mongoSettingsIn(Test)
	
	private val FixtureFile = """([^.]*)\.([^.]*)\.json""".r
	def mongoCollectionName(basename : String) : (String, String) = basename match {
		case FixtureFile(dbname, name) => (dbname, name)
	}
	private val MigrationFile = """([^.]*)\.migration\.js""".r
	def mongoMigrationName(basename : String) : (String) = basename match {
		case MigrationFile(dbname) => (dbname)
	}
}