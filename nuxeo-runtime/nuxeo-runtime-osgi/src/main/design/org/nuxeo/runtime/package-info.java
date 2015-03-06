/**
 * @author Stephane Lacoin at Nuxeo (aka matic)
 *
 * @startuml
 * hide members
 * namespace marketplace {
 *   interface Package
 *   interface Files
 *   interface Script
 *   Package o..> Files : contains
 *   Package o..> Script : runs
 * }
 * namespace configuration {
 *   interface Template
 * }
 * namespace standalone {
 *   interface Packager
 *   interface Config
 *   interface Properties
 *   interface Bundle
 *   interface Library
 *   interface Fragment
 *   Bundle o..> Fragment
 *   Packager ..|> Bundle
 * }
 * namespace container {
 *   interface Nuxeo
 *   interface Console
 *   Console ..> Nuxeo : controls
 *   Console -right..> marketplace
 *   Console ..> standalone.Packager : < war
 * }
 * marketplace ..> standalone
 * marketplace ..> configuration
 * standalone <.. configuration
 * @enduml
 *
 * @startuml
 * rectangle standalone {
 *   (console) -left.> (start)
 *   (console) -up.> (stop)
 *   (console) -right.> (refresh)
 *   (start) -down.> (run)
 *   (run)
 *   (package)
 * }
 *  rectangle marketplace {
 *   (configure)
 *   (configure) -right.> (install)
 *   (configure) -up.> (uninstall)
 *   (configure) -down.> (upgrade)
 * }
 * rectangle container {
 *  (web console) -up.> (configure nuxeo)
 *  (web console) -down.> (start nuxeo)
 *  (web console) -down.> (stop nuxeo)
 *  (web console) -left.> (update nuxeo)
 *  (configure nuxeo) -left.> (configure)
 *  (configure nuxeo) -up.> (package)
 * }
 * User -> (configure)
 * User -left-> (console)
 * User -left-> (run)
 * User -> (web console)
 * @enduml
 *
 */
package org.nuxeo.runtime;