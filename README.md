# Souped versions for various rule-based Lucene/Elasticsearch token filters. Uber filters!

Part of a much bigger closed-source project, this version only provides database access for the various filter rules. If there is interest, I can add refreshable rules (which will only affect newly indexed content), S3 access, and more.

There is only currently support for Elasticsearch 5.5. The main source is easily ported to other versions, but the integration testing support improved with Elasticsearch 5.5.

## Token filters


| uber_keyword_marker | Keyword Marker Token Filter (lacks supports for patterns) |
| uber_stemmer_override | Stemmer Override Token Filter |
| uber_stop | Stop Token Filter |
| uber_synonym | Synonym Token Filter |

All filters are identical to their standard counterpart, but simply add the a **'query'** parameter. If the **'query'** parameter is not provided, the token filter will simply use the standard parameters for its standard counterpart. Any SQL select supported by your database can be used.

## Building

There is no downloadable version of the plugin for two reasons:
1. It is difficult to release a plugin for each minor version of Elasticsearch. You can only run plugins built for the exact version of Elasticsearch.
2. Each database requires a different Java driver, so it would be impossible to include them all.


You can either create a plugin jar with standard Java driver jars or without if you wish to bundle it yourself. Certain drivers, such as the one for MS SQL Server, are not available at the standard maven jar repositories.

- Create a jar with no driver and no tests: `gradle assemble`
- Create a jar with no driver and (non-database) tests: `gradle build`
- Create a jar with the derby driver and full tests: `gradle build -DdbType=test`

You can add a driver jar during build time by use the dbType parameter. 

    gradle build -DdbType=mysql

Currently supported types are postgres, mysql and (networked) derby. The version is specified in the gradle.properties file. Pull requests welcome for other databases.

You can also add the database dependency directly via the dbDependency parameter

    gradle build -DdbDependency=mysql:mysql-connector-java:5.1.44

If you wish to add the Java driver jars manually, you can use any archiving tool with the resulting zip file. Elasticsearch does not use uberjars, so each jar can be added separately. Beware of jarhell issues. You have been warned!

Running `gradle clean` is highly recommended before doing integration tests.

## Installation

After the jar has been built and any additional jars manually added, you can run the elasticsearch plugin installer
$ES_HOME/bin/elasticsearch-plugin install <path to zip file> (usually $PLUGIN_HOME/build/distributions/uber-filters-1.0.zip)

You will be prompted to accept addtional security updates required for database access.

Due to limitations stemming from the Elasticsearch security model, Java drivers are loaded via Class.forName(<driver class>) method and cannot be loaded via JDBC4/DriverManager/ServiceLoader. Therefore the name of the driver class must be specified in the Elasticsearch config. Only one database is allowed, so the settings are defined in the Elasticsearch config (elaticsearch.yml), and not the token filter setup.

Required settings

- uber_filters.jdbc.driver
- uber_filters.jdbc.url

Example

    uber_filters.jdbc.driver: "com.mysql.jdbc.Driver"
    uber_filters.jdbc.url: "jdbc:mysql://localhost/test"

## Examples

    PUT /mytest
    {
      "settings": {
        "index": {
          "analysis": {
            "filter": {
              "mystop": {
                "type": "uber_stop",
                "query": "select distinct stopword from stopwords"
              },
              "mykeyword": {
                "type": "uber_keyword_marker",
                "query": "select distinct keyword_marker from keyword_markers"
              },
              "mystemmeroverride": {
                "type": "uber_stemmer_override",
                "query": "select distinct stemmer_override from stemmer_overrides"
              },
              "mysynonym": {
                "type": "uber_synonym",
                "query": "select distinct synonym from synonyms"
              }          
            }        
          }
        }
      }
    }

The standard parameters will be used if the query is not specified or causes an exception

    PUT /mytest
    {
      "settings": {
        "index": {
          "analysis": {
            "filter": {
              "mystop": {
                "type": "uber_stop"
              },        
              "mysynonym": {
                "type": "uber_synonym",
                "query": "select distinct synonym from synonyms",
                "synonyms" : [
                                "i-pod, i pod => ipod",
                                "universe, cosmos"
                ]
              }          
            }        
          }
        }
  }
}

## NOTICE
The database needs to be up and running with the correct content whenever the token filter is created. Creation can occur when:
 - creating a new index referencing the filter
 - a closed index is re-opened
 - the Elasticsearch node is restarted
 
 Obviously, the database needs to be accessible from each Elasticsearch node. The flexibility having the rules in a database does not comes cheap! :)

### TODO
- Elasticsearch 6 support
- jdbc.url should not be per node, but per token filter
- support for synonym graph
- keyword marker pattern support. Easy enough, might be easier to create a new type altogher (ala synonym graph)
- configurable strict mode that will allow the filter not to use standard rules if the SQL query should fail.
- s3 support


### Pull requests welcome.