## The global picture

Logging is the primary means for investigating and solving problems on production. 
You cannot attach a debugger on server.
On production, only errors are logged, which don't provide in the major cases enough information
for solving. Increasing the log levels will produce thousands of logs which proves to be an
enormous performance penalty not supported on the platform (ie: text processing and disk usage).

Just in time logging solve the problem by diverting the logging events related to a user transaction until
it's completion. At completion time, if the user transaction was successful, all the logging events are cleared
and replaced by a single message. But in the opposite, is the user transaction was a failure, all the events 
are processed, as normal.

## Use cases

When running tests on QA, we need logging for later
investigation. But the amount of logs involve too much CPU time and disk space. By serializing 
logs on files only on test failure, that will solve the problem.

At runtime, we can setup a context by transaction which will be used for serializing
logs only on transaction failure. This can be improved in the case of web requests with 
the help of the web request status and in the case of the asynchronous works the help of the work schedule path.

## Design

We want the application logging as normal unless a just in time context is requested. Log4j does not provide us a way 
of implementing multiple logging contexts. The more suitable framework that cover our need is at this time Logback. 
In logback, we just have to provide a context selector and an extended API onto.

We also require that loggers being resolved dynamically at each call. This is achieved by adapting ourself the commons
logging API.

## Implementation

As said previously we can't rely onto the commons logging bridge provided by slf4j. We're injecting our own log factory 
instead through the mean of a new fragment (nuxeo-runtime-jcl-adapter). This way we can have a full control of the logger
resolution and also we don't have to modify the classpath at runtime (ie: replacing commons-logging with the sl4j bridge).

Then we're replacing the default logback context selector with our own implementation which integrate just in time  in logback. 

The just in time allocation request for a scope which is responsible for the logging context selection. For example the
application scope register a singleton which is always returned. 
