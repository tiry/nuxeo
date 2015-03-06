/**
 * @author Stephane Lacoin at Nuxeo (aka matic)
 *
 * @startuml
 *           hide members
 *           class Registry
 *           class LifeCycle
 *           class Activator
 *           class Files
 *           class StartLevel
 *           interface Bundle
 *           namespace Registry {
 *           class Registration
 *           Registry *-> Registration
 *           }
 *           namespace LifeCycle {
 *           class State
 *           }
 * @enduml
 *
 */
package org.nuxeo.osgi;