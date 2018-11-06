# Java Style Guide

###### This is a modified version of the excellent [Twitter Java Style Guide](https://github.com/twitter/commons/blob/master/src/java/com/twitter/common/styleguide.md).

The intention of this guide is to provide a set of conventions that encourage good code.
It is the distillation of many combined man-years of software engineering and Java development experience.  While some suggestions are more strict than others, you should always practice good judgement.

If following the guide causes unnecessary hoop-jumping or otherwise less-readable code, then *readability trumps the guide*. However, if the more 'readable' variant comes with perils or pitfalls, readability may be sacrificed.

In general, much of our style and conventions mirror the
[Code Conventions for the Java Programming Language](http://www.oracle.com/technetwork/java/codeconvtoc-136057.html)
and [Google's Java Style Guide](https://google.github.io/styleguide/javaguide.html).


## Recommended reading
- [Effective Java](http://www.amazon.com/Effective-Java-Edition-Joshua-Bloch/dp/0321356683)

- [Java Concurrency in Practice](http://jcip.net/)

- [Code Complete 2](http://www.stevemcconnell.com/cc.htm)<br />
  Not java-specific, but a good handbook for programming best-practices.

## Table of Contents
  * [Coding style](#coding-style)                                                                                             
     * [Formatting](#formatting)                                                                                              
        * [Use line breaks wisely](#use-line-breaks-wisely)                                                                   
        * [Indent style](#indent-style)                                                                                       
           * [Chained method calls](#chained-method-calls)                                                                    
        * [No tabs](#no-tabs)                                                                                                 
        * [120 column limit](#120-column-limit)                                                                               
        * [CamelCase for types, camelCase for variables, UPPER_SNAKE for constants](#camelcase-for-types-camelcase-for-variables-upper_snake-for-constants)                                                                                                     
        * [No trailing whitespace](#no-trailing-whitespace)                                                                   
     * [Field, class, and method declarations](#field-class-and-method-declarations)                                          
           * [Modifier order](#modifier-order)                                                                                
     * [Variable naming](#variable-naming)                                                                                    
        * [Extremely short variable names should be reserved for instances like loop indices.](#extremely-short-variable-names-should-be-reserved-for-instances-like-loop-indices)                                                                              
        * [Include units in variable names](#include-units-in-variable-names)                                                 
        * [Don't embed metadata in variable names](#dont-embed-metadata-in-variable-names)                                    
     * [Space pad operators and equals.](#space-pad-operators-and-equals)                                                     
     * [Be explicit about operator precedence](#be-explicit-about-operator-precedence)                                        
     * [Documentation](#documentation)                                                                                        
        * ["I'm writing a report about..."](#im-writing-a-report-about)                                                       
        * [Documenting a class](#documenting-a-class)                                                                         
        * [Documenting a method](#documenting-a-method)                                                                       
        * [Be professional](#be-professional)
        * [Don't document overriding methods (usually)](#dont-document-overriding-methods-usually)
        * [Use javadoc features](#use-javadoc-features)
           * [No author tags](#no-author-tags)
     * [Imports](#imports)
        * [Import ordering](#import-ordering)
        * [Avoid wildcard imports](#avoid-wildcard-imports)
     * [Use interfaces](#use-interfaces)
        * [Leverage or extend existing interfaces](#leverage-or-extend-existing-interfaces)
  * [Writing testable code](#writing-testable-code)
     * [Fakes and mocks](#fakes-and-mocks)
     * [Let your callers construct support objects](#let-your-callers-construct-support-objects)
     * [Testing multithreaded code](#testing-multithreaded-code)
     * [Testing antipatterns](#testing-antipatterns)
        * [Time-dependence](#time-dependence)
        * [The hidden stress test](#the-hidden-stress-test)
        * [Use JMH for running benchmarks and stress tests](#use-jmh-for-running-benchmarks-and-stress-tests)
        * [Thread.sleep()](#threadsleep)
     * [Avoid randomness in tests](#avoid-randomness-in-tests)
  * [Best practices](#best-practices)
     * [Defensive programming](#defensive-programming)
        * [Avoid assert](#avoid-assert)
        * [Preconditions](#preconditions)
        * [Minimize visibility](#minimize-visibility)
        * [Favor immutability](#favor-immutability)
        * [Be wary of null](#be-wary-of-null)
        * [Clean up with finally](#clean-up-with-finally)
     * [Clean code](#clean-code)
        * [Disambiguate](#disambiguate)
        * [Remove dead code](#remove-dead-code)
        * [Use specific abstract types to declare method return types](#use-specific-abstract-types-to-declare-method-return-types)
        * [Always use type parameters](#always-use-type-parameters)
        * [Stay out of <a href="http://en.wikipedia.org/wiki/Texas-sized" rel="nofollow">Texas</a>](#stay-out-of-texas)
        * [Avoid typecasting](#avoid-typecasting)
        * [Use final fields](#use-final-fields)
        * [Avoid mutable static state](#avoid-mutable-static-state)
        * [Exceptions](#exceptions)
           * [Catch narrow exceptions](#catch-narrow-exceptions)
           * [Don't swallow exceptions](#dont-swallow-exceptions)
           * [When interrupted, reset thread interrupted state](#when-interrupted-reset-thread-interrupted-state)
           * [Throw appropriate exception types](#throw-appropriate-exception-types)
     * [Use newer/better libraries](#use-newerbetter-libraries)
        * [StringBuilder over StringBuffer](#stringbuilder-over-stringbuffer)
        * [ScheduledExecutorService over Timer](#scheduledexecutorservice-over-timer)
        * [List over Vector](#list-over-vector)
     * [equals() and hashCode()](#equals-and-hashcode)
     * [Premature optimization is the root of all evil.](#premature-optimization-is-the-root-of-all-evil)
     * [TODOs](#todos)
        * [TODOs should not reach production code](#todos-should-not-reach-production-code)
        * [Open issues on uncompleted TODOs](#open-issues-on-uncompleted-todos)
     * [Obey the Law of Demeter (<a href="http://en.wikipedia.org/wiki/Law_of_Demeter" rel="nofollow">LoD</a>)](#obey-the-law-of-demeter-lod)
        * [In classes](#in-classes)
        * [In methods](#in-methods)
     * [Don't Repeat Yourself (<a href="http://en.wikipedia.org/wiki/Don't_repeat_yourself" rel="nofollow">DRY</a>)](#dont-repeat-yourself-dry)
        * [Extract constants whenever it makes sense](#extract-constants-whenever-it-makes-sense)
        * [Centralize duplicate logic in utility functions](#centralize-duplicate-logic-in-utility-functions)
     * [Manage threads properly](#manage-threads-properly)
     * [Avoid unnecessary code](#avoid-unnecessary-code)
        * [Superfluous temporary variables.](#superfluous-temporary-variables)
        * [Unneeded assignment.](#unneeded-assignment)
     * [The 'fast' implementation](#the-fast-implementation)


## Coding style

### Formatting
Please use the Eclipse formatting files `format_settings.epf` and `format.importorder` in the main directory of the repository. IntelliJ users can use the [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter)

#### Use line breaks wisely
There are generally two reasons to insert a line break:

1. Your statement exceeds the column limit.

2. You want to logically separate a thought.<br />
Writing code is like telling a story.  Written language constructs like chapters, paragraphs,
and punctuation (e.g. semicolons, commas, periods, hyphens) convey thought hierarchy and
separation.  We have similar constructs in programming languages; you should use them to your
advantage to effectively tell the story to those reading the code.

#### Indent style
Every logical block should have braces, even if it is a one-liner.
Indent size is 4 columns.

    
    // Like this
    if (x < 0) {
      negative(x);
    } else {
      nonnegative(x);
    }

    // Not like this.
    if (x < 0)
      negative(x);

    // Also not like this.
    if (x < 0) negative(x);

Continuation indent is 8 columns.  Nested continuations should add 4 columns at each level.

    
    // Bad.
    //   - Line breaks are arbitrary.
    //   - Scanning the code makes it difficult to piece the message together.
    throw new IllegalStateException("Failed to process request" + request.getId()
        + " for user " + user.getId() + " query: '" + query.getText()
        + "'");

    // Good.
    //   - Each component of the message is separate and self-contained.
    //   - Adding or removing a component of the message requires minimal reformatting.
    throw new IllegalStateException("Failed to process"
        + " request " + request.getId()
        + " for user " + user.getId()
        + " query: '" + query.getText() + "'");

Don't break up a statement unnecessarily.

    
    // Bad.
    String value =
        otherValue;

    // Good.
    String value = otherValue;

Method declaration continuations.

    
    // Sub-optimal since line breaks are arbitrary and only filling lines.
    String downloadAnInternet(Internet internet, Tubes tubes,
        Blogosphere blogs, Amount<Long, Data> bandwidth) {
      tubes.download(internet);
      ...
    }

    // Acceptable.
    String downloadAnInternet(Internet internet, Tubes tubes, Blogosphere blogs,
        Amount<Long, Data> bandwidth) {
      tubes.download(internet);
      ...
    }

    // Nicer, as the extra newline gives visual separation to the method body.
    String downloadAnInternet(Internet internet, Tubes tubes, Blogosphere blogs,
        Amount<Long, Data> bandwidth) {

      tubes.download(internet);
      ...
    }

    // Also acceptable, but may be awkward depending on the column depth of the opening parenthesis.
    public String downloadAnInternet(Internet internet,
                                     Tubes tubes,
                                     Blogosphere blogs,
                                     Amount<Long, Data> bandwidth) {
      tubes.download(internet);
      ...
    }

    // In case column space is an issue this is the preferred solution.
    public String downloadAnInternet(
        Internet internet,
        Tubes tubes,
        Blogosphere blogs,
        Amount<Long, Data> bandwidth) {

      tubes.download(internet);
      ...
    }

##### Chained method calls

    
    // Bad.
    //   - Line breaks are based on line length, not logic.
    Iterable<Module> modules = ImmutableList.<Module>builder().add(new LifecycleModule())
        .add(new AppLauncherModule()).addAll(application.getModules()).build();

    // Better.
    //   - Calls are logically separated.
    //   - However, the trailing period logically splits a statement across two lines.
    Iterable<Module> modules = ImmutableList.<Module>builder().
        add(new LifecycleModule()).
        add(new AppLauncherModule()).
        addAll(application.getModules()).
        build();

    // Good and the way it should be done.
    //   - Method calls are isolated to a line.
    //   - The proper location for a new method call is unambiguous.
    Iterable<Module> modules = ImmutableList.<Module>builder()
        .add(new LifecycleModule())
        .add(new AppLauncherModule())
        .addAll(application.getModules())
        .build();

#### No tabs
An oldie, but goodie.  We've found tab characters to cause more harm than good.

#### 120 column limit
You should follow the convention set by the body of code you are working with.
We tend to use 120 columns for a balance between fewer continuation lines but still easily
fitting the code nicely for the GitHub viewer.

#### CamelCase for types, camelCase for variables, UPPER_SNAKE for constants

#### No trailing whitespace
Trailing whitespace characters, while logically benign, add nothing to the program.
However, they do serve to frustrate developers when using keyboard shortcuts to navigate code.

### Field, class, and method declarations

##### Modifier order

We follow the [Java Language Specification](http://docs.oracle.com/javase/specs/) for modifier
ordering (sections
[8.1.1](http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.1.1),
[8.3.1](http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.3.1) and
[8.4.3](http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.4.3)).

    
    // Bad.
    final volatile private String value;

    // Good.
    private final volatile String value;

### Variable naming

#### Extremely short variable names should be reserved for instances like loop indices.

    
    // Bad.
    //   - Field names give little insight into what fields are used for.
    class User {
      private final int a;
      private final String m;

      ...
    }

    // Good.
    class User {
      private final int ageInYears;
      private final String maidenName;

      ...
    }

#### Include units in variable names

    
    // Bad.
    long pollInterval;
    int fileSize;

    // Good.
    long pollIntervalMs;
    int fileSizeGb.

    // Better.
    //   - Unit is built in to the type.
    //   - The field is easily adaptable between units, readability is high.
    Amount<Long, Time> pollInterval;
    Amount<Integer, Data> fileSize;

#### Don't embed metadata in variable names
A variable name should describe the variable's purpose.  Adding extra information like scope and
type is generally a sign of a bad variable name.

Avoid embedding the field type in the field name.

    
    // Bad.
    Map<Integer, User> idToUserMap;
    String valueString;

    // Good.
    Map<Integer, User> usersById;
    String value;

Also avoid embedding scope information in a variable.  Hierarchy-based naming suggests that a class
is too complex and should be broken apart.

    
    // Bad.
    private String _value;
    private String mValue;

    // Good.
    private String value;

### Space pad operators and equals.

    
    // Bad.
    //   - This offers poor visual separation of operations.
    int foo=a+b+1;

    // Good.
    int foo = a + b + 1;

### Be explicit about operator precedence
Don't make your reader open the
[spec](http://docs.oracle.com/javase/tutorial/java/nutsandbolts/operators.html) to confirm,
if you expect a specific operation ordering, make it obvious with parenthesis.

    
    // Bad.
    return a << 8 * n + 1 | 0xFF;

    // Good.
    return (a << ((8 * n) + 1)) | 0xFF;

It's even good to be *really* obvious.

    
    if ((values != null) && (10 > values.size())) {
      ...
    }

### Documentation

The more visible a piece of code is (and by extension - the farther away consumers might be),
the more documentation is needed.

#### "I'm writing a report about..."
Your elementary school teacher was right - you should never start a statement this way.
Likewise, you shouldn't write documentation this way.

    
    // Bad.
    /**
     * This is a class that implements a cache.  It does caching for you.
     */
    class Cache {
      ...
    }

    // Good.
    /**
     * A volatile storage for objects based on a key, which may be invalidated and discarded.
     */
    class Cache {
      ...
    }

#### Documenting interfaces
The documentation for an interface should give you an understanding on what the interface does
without any need to look at its implementation(s). It needs to focus on what the 
interface role is without drifting too much to other parts of the system.
    
    //Bad
    /**
     *
     * Calculates the <tt>rating</tt> for transactions in a subtangle.
     *
     * <p>
     // This line has nothing to do with the current interface
     * We create a subtangle by calculating an entrypoint that starts at given depth (number of milestones
     * beofre the most recent one).
     *
     * During tip selection we perform a random walk on a subtangle until we reach a tip.
     * The <tt>rating</tt> is an integer assigned to a transaction that will
     * determine the probability of being traversed upon during the next step of the random walk.
     * A transaction that has a higher rating compared to other transactions, is more likely to be
     * traversed on during the random walk.
     *
     * </p>
     */
    @FunctionalInterface
    public interface RatingCalculator {
    ...
    }
    
    
    
    //Good
    /**
     *
     * Calculates the <tt>rating</tt> for transactions in a subtangle.
     *
     * <p>
     * During tip selection we perform a random walk on a subtangle until we reach a tip.
     * The <tt>rating</tt> is an integer assigned to a transaction that will
     * determine the probability of being traversed upon during the next step of the random walk.
     * A transaction that has a higher rating compared to other transactions, is more likely to be
     * traversed on during the random walk.
     * </p>
     */
    @FunctionalInterface
    public interface RatingCalculator {

The problems with the example above is:
1. We will probably start having duplicate documentation. So we will have to maintain documentation in more than
one place.
2. We are documenting an interface not the entire system

If one still wants to explain the relationship of this interface to to others the "@see" annotation 
can come in handy.

    //Use @see to point the reader to related modules
    /**
     *
     * Calculates the <tt>rating</tt> for transactions in a subtangle.
     *
     * <p>
     * During tip selection we perform a random walk on a subtangle until we reach a tip.
     * The <tt>rating</tt> is an integer assigned to a transaction that will
     * determine the probability of being traversed upon during the next step of the random walk.
     * A transaction that has a higher rating compared to other transactions, is more likely to be
     * traversed on during the random walk.
     * </p>
     *
     * @see EntryPointSelector
     * @see Walker
     */


#### Documenting a class
Documentation for a class may range from a single sentence
to paragraphs with code examples. Documentation should serve to disambiguate any conceptual
blanks in the API, and make it easier to quickly and *correctly* use your API.
A thorough class doc usually has a one sentence summary and, if necessary,
a more detailed explanation.

    
    /**
     * An RPC equivalent of a unix pipe tee.  Any RPC sent to the tee input is guaranteed to have
     * been sent to both tee outputs before the call returns.
     *
     * @param <T> The type of the tee'd service.
     */
    public class RpcTee<T> {
      ...
    }
    
Also when you are implementing an interface no need for obvious comments

    //Bad
    /**
    * Implements the basic contract of {@link Bar}
    */
    public class BarImpl implements Bar {
    ..
    }
    
    //Good
    /**
     * Utilizes Foo sets in order to bring the system to equilibrium.  
     */
     public class BarImpl implements Bar {
     ..
     }


#### Documenting a method
A method doc should tell what the method *does*.  Depending on the argument types, it may
also be important to document input format.

    
    // Bad.
    //   - The doc tells nothing that the method declaration didn't.
    //   - This is the 'filler doc'.  It would pass style checks, but doesn't help anybody.
    /**
     * Splits a string.
     *
     * @param s A string.
     * @return A list of strings.
     */
    List<String> split(String s);

    // Better.
    //   - We know what the method splits on.
    //   - Still some undefined behavior.
    /**
     * Splits a string on whitespace.
     *
     * @param s The string to split.  A {@code null} string is treated as an empty string.
     * @return A list of the whitespace-delimited parts of the input.
     */
    List<String> split(String s);

    // Great.
    //   - Covers yet another edge case.
    /**
     * Splits a string on whitespace.  Repeated whitespace characters are collapsed.
     *
     * @param s The string to split.  A {@code null} string is treated as an empty string.
     * @return A list of the whitespace-delimited parts of the input.
     */
    List<String> split(String s);
    
#### Getters and Setters
When documenting getters and setters be sure to write meaningful comments. No comments 
are better than trivial comments.
    
    //Bad, here we just clutter the code with meaningless comments.
    public class Bar {
       
        private long foo;
       
       /**
        * gets the foo
        *
        * @return the foo
        */
        public long getFoo() {
           return foo;
        }
      
       /**
        * sets the foo
        *
        * @param foo the foo to set
        */
        public void setFoo(long foo) {
           this.foo = foo;
        }
    }
    
    //Might as well just have no comments
    public class Bar {
           
        private long foo;
       
        public long getFoo() {
           return foo;
        }
       
        public void setFoo(long foo) {
           this.foo = foo;
        }
    }
    
    //Good!
    public class Bar {
       
        private static final long MAX_FOO = 100;
      
        private long foo;
       
       /**
        * Gets the adjustment factor used in the Bar-calculation. It has a default
        * value per Baz type, but it can be manually adjusted
        *
        * @return a value between 0 and {@link #MAX_FOO} inclusive.
        */
        public long getFoo() {
           return foo;
        }
      
      /**
       * Foo is the adjustment factor used in the Bar-calculation. It has a default
       * value depending on the Baz type, but can be adjusted on a per-case base.
       * 
       * @param foo must be greater than 0 and not greater than {@link #MAX_FOO}.
       */
        public void setFoo(long foo) {
           this.foo = foo;
        }
    }
    
    //Also Good!
    //The only problem here is that if we want to generate the javadocs html
    //then we need to configure the doclet to generate docs for private methods.
    //Non-issue if you don't generate the docs.
    public class Bar {
       
        private static final long MAX_FOO = 100;
         
      /**
       * The adjustment factor used in the Bar-calculation. It has a default
       * value depending on the Baz type, but can be adjusted on a per-case base.
       * Foo must be greater than 0 and not greater than {@link #MAX_FOO}.
       */
        private long foo;
       
       /**
        *
        * @return {@link #foo}
        */
        public long getFoo() {
           return foo;
        }
      
      /**
       * 
       * @param {@link #foo}
       */
        public void setFoo(long foo) {
           this.foo = foo;
        }
    }
    

#### Be professional
We've all encountered frustration when dealing with other libraries, but ranting about it doesn't
do you any favors.  Suppress the expletives and get to the point.

    
    // Bad.
    // I hate xml/soap so much, why can't it do this for me!?
    try {
      userId = Integer.parseInt(xml.getField("id"));
    } catch (NumberFormatException e) {
      ...
    }

    // Good.
    // ISSUE #XXX: Tuck field validation away in a library.
    try {
      userId = Integer.parseInt(xml.getField("id"));
    } catch (NumberFormatException e) {
      ...
    }

#### Don't document overriding methods (usually)

    
    interface Database {
      /**
       * Gets the installed version of the database.
       *
       * @return The database version identifier.
       */
      String getVersion();
    }

    // Bad.
    //   - Overriding method doc doesn't add anything.
    class PostgresDatabase implements Database {
      /**
       * Gets the installed version of the database.
       *
       * @return The database version identifier.
       */
      @Override
      public String getVersion() {
        ...
      }
    }

    // Good.
    class PostgresDatabase implements Database {
      @Override
      public int getVersion();
    }

    // Great.
    //   - The doc explains how it differs from or adds to the interface doc.
    class TwitterDatabase implements Database {
      /**
       * Semantic version number.
       *
       * @return The database version in semver format.
       */
      @Override
      public String getVersion() {
        ...
      }
    }
    
    //Also Great
    //Inherit the doc and add more info
     class TwitterDatabase implements Database {
          /**
           * {@inheritDoc}
           *
           * @return version in format X.X.X.X
           */
          @Override
          public String getVersion() {
            ...
          }
        }
    

#### Use javadoc features
You can use html tags:
\<pre>,\<tt>,\<b>,\<p>, and etc.

Also use javadocs tags such as {@link}, {@code}, {@param}, {@see}, and {@return}

##### No author tags
Code can change hands numerous times in its lifetime, and quite often the original author of a
source file is irrelevant after several iterations.  We find it's better to trust commit history.

##### No need to overuse {@link}

Remember that javadocs automatically generate links for return types for us.

    //Bad, the doclet will generate a link anyhows.   
    /**
    * @return the {@link #Foo.Bar}
    */
    public Bar getBar() 
    
    //Better 
    /**
    * @return the Bar that facilitates the Foo
    */
    public Bar getBar() 

### Imports

#### Import ordering
Imports are grouped by top-level package, with blank lines separating groups. Static imports should be avoided, but are allowed in test classes.

    
    import com.iota.*

    import java.*
    
    import javax.*

    import com.*

    import net.*

    import org.*

    import static *

#### Avoid wildcard imports
Wildcard imports make the source of an imported class less clear.  They also tend to hide a high
class [fan-out](http://en.wikipedia.org/wiki/Coupling_(computer_programming)#Module_coupling). However, if you have an imports that exceed more than 5 packages/classes you can assume that you are specific enough. Especially if you have lots of imports from the specific subpackage. <br />
*See also [texas imports](#stay-out-of-texas)*

    
    // Bad.
    //   - Where did Foo come from?
    import com.iota.baz.foo.*;
    import com.iota.*;
    import com.iota.bar.foo.collections.*

    interface Bar extends Foo {
        CustomMap createMap();
        CustomList createList();
      ...
    }

    // Good.
    import com.iota.baz.foo.BazFoo;
    import com.iota.Foo;
    //This is fine because it is long enough and a specific subpackage was specified.
    import com.iota.bar.foo.collections.*

    interface Bar extends Foo {
        CustomMap createMap();
        CustomList createList();
      ...
    }


### Use interfaces
Interfaces decouple functionality from implementation, allowing you to use multiple implementations
without changing consumers.
Interfaces are a great way to isolate packages - provide a set of interfaces, and keep your
implementations package private.

Many small interfaces can seem heavyweight, since you end up with a large number of source files.
Consider the pattern below as an alternative.

    
    interface FileFetcher {
      File getFile(String name);

      // All the benefits of an interface, with little source management overhead.
      // This is particularly useful when you only expect one implementation of an interface.
      static class HdfsFileFetcher implements FileFetcher {
        @Override File getFile(String name) {
          ...
        }
      }
    }

#### Leverage or extend existing interfaces
Sometimes an existing interface allows your class to easily 'plug in' to other related classes. This leads to highly [cohesive](http://en.wikipedia.org/wiki/Cohesion_(computer_science)) code.

Examples of common interfaces that are extended are [Cloneable](https://docs.oracle.com/javase/8/docs/api/java/lang/Cloneable.html), [Readable](https://docs.oracle.com/javase/8/docs/api/java/lang/Readable.html), [Appendable](https://docs.oracle.com/javase/8/docs/api/java/lang/Appendable.html), [AutoClosable](https://docs.oracle.com/javase/8/docs/api/java/lang/AutoCloseable.html),
 [Iterable](https://docs.oracle.com/javase/8/docs/api/java/lang/Iterable.html), [Comparable](https://docs.oracle.com/javase/8/docs/api/java/lang/Iterable.html), [Runnable](https://docs.oracle.com/javase/8/docs/api/java/lang/Runnable.html), and [Callable](https://docs.oracle.com/javase/8/docs/api/java/lang/Runnable.html).
  
    
    // An unfortunate lack of consideration.  Anyone who wants to interact with Blobs will need to
    // write specific glue code.
    class Blobs {
      byte[] nextBlob() {
        ...
      }
    }

    // Much better.  Now the caller can easily adapt this to standard collections, or do more
    // complex things like filtering.
    class Blobs implements Iterable<byte[]> {
      @Override
      Iterator<byte[]> iterator() {
        ...
      }
    }

Warning - don't bend the definition of an existing interface to make this work.  If the interface
doesn't conceptually apply cleanly, it's best to avoid this.

#### Use functional interfaces
 Any interface that has exactly one abstract method is a functional interface that can be used with lambda expressions. When one creates a new functional interface it is encouraged to add the optional `@FunctionalInterface` annotation to ensure than no more than one abstract method will ever be added. One should use built in functional interfaces such as: 
[Supplier](https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html), [Consumer](https://docs.oracle.com/javase/8/docs/api/java/util/function/Consumer.html), [Predicate](https://docs.oracle.com/javase/8/docs/api/java/util/function/Predicate.html), [UnaryOperator](https://docs.oracle.com/javase/8/docs/api/index.html?java/util/function/UnaryOperator.html), [Function](https://docs.oracle.com/javase/8/docs/api/index.html?java/util/function/Function.html), and anything else that can be found in `java.uti.function.*`.

Note that old interfaces prior to Java 8 such as `Runnable`, `Callable`, and `Comparable` are also functional.
 
 
     
    //Example of how the Predicate interface is used
    List<String> names = Arrays.asList("Angela", "Aaron", "Bob", "Claire", "David");
     
    List<String> namesWithA = names.stream()
       //filter takes in a Predicate
      .filter(name -> name.startsWith("A"))
      .collect(Collectors.toList());

## Writing testable code
Writing unit tests doesn't have to be hard.  You can make it easy for yourself if you keep
testability in mind while designing your classes and interfaces.

### Fakes and mocks
When testing a class, you often need to provide some kind of canned functionality as a replacement
for real-world behavior.  For example, rather than fetching a row from a real database, you have a test row that you want to return.  This is most commonly performed with a fake object or a mock object.  While the difference sounds subtle, mocks have major benefits over fakes.

Let's look at a bad example:

    
    class RpcClient {
      RpcClient(HttpTransport transport) {
        ...
      }
    }

    // Bad.
    //   - Our test has little control over method call order and frequency.
    //   - We need to be careful that changes to HttpTransport don't disable FakeHttpTransport.
    //   - May require a significant amount of code.
    class FakeHttpTransport extends HttpTransport {
      @Override
      void writeBytes(byte[] bytes) {
        ...
      }

      @Override
      byte[] readBytes() {
        ...
      }
    }

    public class RpcClientTest {
      private RpcClient client;
      private FakeHttpTransport transport;

      @Before
      public void setUp() {
        transport = new FakeHttpTransport();
        client = new RpcClient(transport);
      }

      ...
    }

    interface Transport {
      void writeBytes(byte[] bytes);
      byte[] readBytes();
    }

    class RpcClient {
      RpcClient(Transport transport) {
        ...
      }
    }

Good example:

    
    // Good.
    //   - We can mock the interface and have very fine control over how it is expected to be used.
    public class RpcClientTest {
      private RpcClient client;
      private Transport transport;

      @Before
      public void setUp() {
        transport = Mockito.mock(Transport.class);
        client = new RpcClient(transport);
      }

      ...
    }
    
     // Better
    //   - Use annotations.
    @RunWith(MockitoJUnitRunner.class)
    public class RpcClientTest {
      private RpcClient client;
      @Mock
      private Transport transport;

      @Before
      public void setUp() {
     
        client = new RpcClient(transport);
      }

      ...
    }

### Let your callers construct support objects

    
    // Bad.
    //   - A unit test needs to manage a temporary file on disk to test this class.
    class ConfigReader {
      private final InputStream configStream;
      ConfigReader(String fileName) throws IOException {
        this.configStream = new FileInputStream(fileName);
      }
    }

    // Good.
    //   - Testing this class is as easy as using ByteArrayInputStream with a String.
    class ConfigReader {
      private final InputStream configStream;
      ConfigReader(InputStream configStream){
        this.configStream = checkNotNull(configStream);
      }
    }

### Testing multithreaded code
Testing code that uses multiple threads is notoriously hard.  When approached carefully, however,
it can be accomplished without deadlocks or unnecessary time-wait statements.

If you are testing code that needs to perform periodic background tasks
(such as with a
[ScheduledExecutorService](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledExecutorService.html)),
consider mocking the service and/or manually triggering the tasks from your tests, and
avoiding the actual scheduling.
If you are testing code that submits tasks to an
[ExecutorService](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html),
you might consider allowing the executor to be injected, and supplying a
[single-thread executor](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Executors.html#newSingleThreadExecutor()) in tests.

In cases where multiple threads are inevitable,
[java.util.concurrent](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/package-summary.html)
provides some useful libraries to help manage lock-step execution.

For example,
[LinkedBlockingDeque](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/LinkedBlockingDeque.html)
can provide synchronization between producer and consumer when an asynchronous operation is
performed.
[CountDownLatch](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/CountDownLatch.html)
is useful for state/operation synchronization when a queue does not apply.

### Testing antipatterns

#### Time-dependence
Code that captures real wall time can be difficult to test repeatably, especially when time deltas
are meaningful.  Therefore, try to avoid `new Date()`, `System.currentTimeMillis()`, and
`System.nanoTime()`.  A suitable replacement for these is [Clock](https://github.com/twitter/commons/blob/master/src/java/com/twitter/common/util/Clock.java); using [Clock.SYSTEM_CLOCK](https://github.com/twitter/commons/blob/master/src/java/com/twitter/common/util/Clock.java#L32)
when running normally, and [FakeClock](https://github.com/twitter/commons/blob/master/src/java/com/twitter/common/util/testing/FakeClock.java) in tests.

#### The hidden stress test
Avoid writing unit tests that attempt to verify a certain amount of performance.  This type of
testing should be handled separately, and run in a more controlled environment than unit tests typically are. 

*Exception*: You can still choose to use the unit test runner for such tests, but they must be **excluded** from the unit test the build tool runs.

#### Use JMH for running benchmarks and stress tests
In order to create benchmarks it is advised to use [JMH](http://tutorials.jenkov.com/java-performance/jmh.html). *One must run those tests on a clean controlled environment in order to obtain meaningful results*. One can use the `JunitRunner` to perform assertions on the results. Of course if you use JUnit you must make sure it is excluded from your other unit tests.

    
    public class BenchmarkRunner {

    @Test
    public void launchDbBenchmarks() throws RunnerException {
        Options opts = new OptionsBuilder()
                .include(RocksDbBenchmark.class.getName() + ".*")
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.MILLISECONDS)
                .warmupIterations(5)
                .forks(1)
                .measurementIterations(10)
                .shouldFailOnError(true)
                .shouldDoGC(false)
                .build();

        //possible to do assertions over run results
        Collection<RunResult> runResults = new Runner(opts).run();
    }



#### Thread.sleep()
Sleeping is rarely warranted, especially in test code.  Sleeping is expressing an expectation that
something else is happening while the executing thread is suspended.  This quickly leads to
brittleness; for example if the background thread was not scheduled while you were sleeping.

Sleeping in tests is also bad because it sets a firm lower bound on how fast tests can execute.
No matter how fast the machine is, a test that sleeps for one second can never execute in less than
one second.  Over time, this leads to very long test execution cycles.

### Avoid randomness in tests
Using random values may seem like a good idea in a test, as it allows you to cover more test cases with less code.  The problem is that you lose control over which test cases you're covering.  When you do encounter a test failure, it may be difficult to reproduce. Pseudorandom input with a fixed seed is slightly better, but in practice rarely improves test coverage.  In general it's better to use fixed input data that exercises known edge cases.

## Best practices

### Defensive programming

#### Avoid assert
We avoid the assert statement since it can be
[disabled](http://docs.oracle.com/javase/7/docs/technotes/guides/language/assert.html#enable-disable) at execution time, and prefer to enforce these types of invariants at all times.

*See also [preconditions](#preconditions)*

#### Preconditions
Preconditions checks are a good practice, since they serve as a well-defined barrier against bad input from callers.  As a convention, object parameters to public constructors and methods should always be checked against null, unless null is explicitly allowed. It is advised to use `Objects.requireNonNull(object,"Informative error message")` to throw a preemptive `NullPointerExceptions`.

*See also [be wary of null](#be-wary-of-null)*

    
    // Bad.
    //   - If the file or callback are null, the problem isn't noticed until much later.
    class AsyncFileReader {
      void readLater(File file, Closure<String> callback) {
        scheduledExecutor.schedule(new Runnable() {
          @Override public void run() {
            callback.execute(readSync(file));
          }
        }, 1L, TimeUnit.HOURS);
      }
    }

    // Good.
    class AsyncFileReader {
      void readLater(File file, Closure<String> callback) {
        Objects.requireNonNull(file, "No file was given to read");
        checkArgument(file.exists() && file.canRead(), "File must exist and be readable.");
        Objects.requireNonNull(callback, "No operation defined to how to read files");

        scheduledExecutor.schedule(new Runnable() {
          @Override public void run() {
            callback.execute(readSync(file));
          }
        }, 1L, TimeUnit.HOURS);
      }
    }

#### Minimize visibility

In a class API, you should support access to any methods and fields that you make accessible.
Therefore, only expose what you intend the caller to use.  This can be imperative when
writing thread-safe code.

    
    public class Parser {
      // Bad.
      //   - Callers can directly access and mutate, possibly breaking internal assumptions.
      public Map<String, String> rawFields;

      // Bad.
      //   - This is probably intended to be an internal utility function.
      public String readConfigLine() {
        ..
      }
    }

    // Good.
    //   - rawFields and the utility function are hidden
    //   - The class is package-private, indicating that it should only be accessed indirectly.
    class Parser {
      private final Map<String, String> rawFields;

      private String readConfigLine() {
        ..
      }
    }

#### Favor immutability

Mutable objects carry a burden - you need to make sure that those who are *able* to mutate it are not violating expectations of other users of the object, and that it's even safe for them to modify.

    
    // Bad.
    //   - Anyone with a reference to User can modify the user's birthday.
    //   - Calling getAttributes() gives mutable access to the underlying map.
    public class User {
      public Date birthday;
      private final Map<String, String> attributes = Maps.newHashMap();

      ...

      public Map<String, String> getAttributes() {
        return attributes;
      }
    }

    // Good.
    public class User {
      private final Date birthday;
      private final Map<String, String> attributes = Maps.newHashMap();

      ...

      public Map<String, String> getAttributes() {
        return ImmutableMap.copyOf(attributes);
      }

      // If you realize the users don't need the full map, you can avoid the map copy
      // by providing access to individual members.
      public Optional<String> getAttribute(String attributeName) {
        return attributes.get(attributeName);
      }
    }

#### Be wary of null
A method should return an `Optional` to indicate the possibility of a null value.
If you want to compare values of nullable vars that for some reason aren't wrapped in an `Optional` you can avoid nasty null pointer exceptions using `Objects.equals()`.

    
    //bad, str is allowed to be null but null pointer excpetion may be thrown
    private static final String MY_STR = "STRING"
    
    public boolean compareStrings(String str) {
        //this may throw a null pointer exception
        return str.equals(MY_STR);
    }
    
    
    //better 
     private static final String MY_STR = "STRING"
        
        public boolean compareStrings(String str) {
            //this is null safe
            return MY_STR.equals(str);
        }
        
    //safe solution 
    private String myStr = "STRING"
            
            public boolean compareStrings(String str) {
                //this is null safe
                return Objects.equals(myStr, str);
            }
    
            
    //preferrable solution: use Optional to express that null is a valid state for the variable
     private static final String MY_STR = "STRING"
                
                public boolean compareStrings(Optional<String> str) {
                    //The user will be wary of null pointer exceptions because of Optional
                    //Without reading the guide he will not put str first.
                    return MY_STR.equals(str.get());
                } 
                
#### Clean up with finally

    
    FileInputStream in = null;
    try {
      ...
    } catch (IOException e) {
      ...
    } finally {
      Closeables.closeQuietly(in);
    }
    
    //better - use autoclose
    try (FileInputStream in = new FileInputStream(file)) {
      ...
    } catch (IOException e) {
      ...
    }

Even if there are no checked exceptions, there are still cases where you should use try/finally
to guarantee resource symmetry.

    
    // Bad.
    //   - Mutex is never unlocked.
    mutex.lock();
    throw new NullPointerException();
    mutex.unlock();

    // Good.
    mutex.lock();
    try {
      throw new NullPointerException();
    } finally {
      mutex.unlock();
    }

    // Bad.
    //   - Connection is not closed if sendMessage throws.
    if (receivedBadMessage) {
      conn.sendMessage("Bad request.");
      conn.close();
    }

    // Good.
    if (receivedBadMessage) {
      try {
        conn.sendMessage("Bad request.");
      } finally {
        conn.close();
      }
    }


### Clean code

#### Disambiguate
Favor readability - if there's an ambiguous and unambiguous route, always favor unambiguous.

    
    // Bad.
    //   - Depending on the font, it may be difficult to discern 1001 from 100l.
    long count = 100l + n;

    // Good.
    long count = 100L + n;

#### Remove dead code
Delete unused code (imports, fields, parameters, methods, classes).  They will only rot.

#### Use specific abstract types to declare method return types
When declaring method return types it is best to strive to be specific yet abstract. The reason is that you don't know what will the callers of the method need. You don't want to degrade functionality too much. The callers can always abstract the returned type farther if needed.

    
    // Bad.
    //   - Implementations of Database must match the ArrayList return type. We need to depend on abstractions not concretions.
    //   - Changing return type to Set<User> or List<User> could break implementations and users.
    interface Database {
      ArrayList<User> fetchUsers(String query);
    }

    //Good
    // Here we stress that we reurn a list of users in a certain order 
     interface Database {
      List<User> fetchUsers(String query);
    }
    
     //Good
    // Here we stress that we reurn a list of unique users in no particular order. If order matters use NavigableSet
     interface Database {
      Set<User> fetchUsers(String query);
    }

    // Good.
    //   - Iterable defines the minimal functionality required of the return.
    //An advantage here is that you have more possibilities to change the implementation.
    //Notice that the program shouldn't break no matter what implementing subclass you use. 
    interface Database {
      Iterable<User> fetchUsers(String query);
    }


#### Always use type parameters
Java 5 introduced support for
[generics](http://docs.oracle.com/javase/tutorial/java/generics/index.html). This added type parameters to collection types, and allowed users to implement their own type-parameterized classes.
Backwards compatibility and [type erasure](http://docs.oracle.com/javase/tutorial/java/generics/erasure.html) mean that type parameters are optional, however depending on usage they do result in compiler warnings.

We conventionally include type parameters on every declaration where the type is parameterized. Even if the type is unknown, it's preferable to include a wildcard or wide type.

#### Stay out of [Texas](http://en.wikipedia.org/wiki/Texas-sized)
Try to keep your classes bite-sized and with clearly-defined responsibilities.  This can be *really* hard as a program evolves.

- texas imports
- texas constructors: Can the class be cleanly broken apart?<br />
If not, consider builder pattern.
- texas methods

We could do some science and come up with a statistics-driven threshold for each of these, but it probably wouldn't be very useful.  This is usually just a gut instinct, and these are traits of classes that are too large or complex and should be broken up.

#### Avoid typecasting
Typecasting is a sign of poor class design, and can often be avoided.  An obvious exception here is overriding [equals](http://docs.oracle.com/javase/7/docs/api/java/lang/Object.html#equals(java.lang.Object)).

#### Use final fields
*See also [favor immutability](#favor-immutability)*

Final fields are useful because they declare that a field may not be reassigned. *Differentiate between fields and local variables. Adding `Final` everywhere may lead to too much verbosity.*

    
    public class TransactionValidator {
    //this should be final if you want the instance to use the same converter by design
    final Converter converter;

    ...
    //usually no need to make parameter final. It has a limited scope anyhows
    public validate(Transaction tx) {
        //If you stay out of texas then no need to make this final either
        String signature;
        ...
   
    }


#### Avoid mutable static state
Mutable static state is rarely necessary, and causes loads of problems when present.  A very simple case that mutable static state complicates is unit testing.  Since unit tests runs are typically in a single VM, static state will persist through all test cases.  In general, mutable static state is a sign of poor class design.

#### Exceptions
##### Catch narrow exceptions
Sometimes when using try/catch blocks, it may be tempting to just `catch Exception`, `Error`, or `Throwable` so you don't have to worry about what type was thrown. This is usually a bad idea, as you can end up catching more than you really wanted to deal with.  For example, `catch Exception` would capture `NullPointerException`, and `catch Throwable` would capture `OutOfMemoryError`.

    
    // Bad.
    //   - If a RuntimeException happens, the program continues rather than aborting.
    try {
      storage.insertUser(user);
    } catch (Exception e) {
      LOG.error("Failed to insert user.");
    }

    try {
      storage.insertUser(user);
    } catch (StorageException e) {
      LOG.error("Failed to insert user.");
    }

##### Don't swallow exceptions
An empty `catch` block is usually a bad idea, as you have no signal of a problem.  Coupled with
[narrow exception](#catch-narrow-exceptions) violations, it's a recipe for disaster.

##### When interrupted, reset thread interrupted state
Many blocking operations throw
[InterruptedException](http://docs.oracle.com/javase/7/docs/api/java/lang/InterruptedException.html)
so that you may be awaken for events like a JVM shutdown.  When catching `InterruptedException`, it is good practice to ensure that the thread interrupted state is preserved.

IBM has a good [article](http://www.ibm.com/developerworks/java/library/j-jtp05236/index.html) on
this topic.

    
    // Bad.
    //   - Surrounding code (or higher-level code) has no idea that the thread was interrupted.
    try {
      lock.tryLock(1L, TimeUnit.SECONDS)
    } catch (InterruptedException e) {
      log.info("Interrupted while doing x");
    }

    // Good.
    //   - Interrupted state is preserved.
    try {
      lock.tryLock(1L, TimeUnit.SECONDS)
    } catch (InterruptedException e) {
      log.info("Interrupted while doing x");
      Thread.currentThread().interrupt();
    }

##### Throw appropriate exception types
Let your API users obey [catch narrow exceptions](#catch-narrow-exceptions), don't throw Exception.
Even if you are calling another naughty API that throws Exception, at least hide that so it doesn't
bubble up even further.  You should also make an effort to hide implementation details from your
callers when it comes to exceptions.

    
    // Bad.
    //   - Caller is forced to catch Exception, trapping many unnecessary types of issues.
    interface DataStore {
      String fetchValue(String key) throws Exception;
    }

    // Better.
    //   - The interface leaks details about one specific implementation.
    interface DataStore {
      String fetchValue(String key) throws SQLException, UnknownHostException;
    }

    // Good.
    //   - A custom exception type insulates the user from the implementation.
    //   - Different implementations aren't forced to abuse irrelevant exception types.
    interface DataStore {
      String fetchValue(String key) throws StorageException;

      static class StorageException extends Exception {
        ...
      }
    }

### Use newer/better libraries

#### StringBuilder over StringBuffer
[StringBuffer](http://docs.oracle.com/javase/7/docs/api/java/lang/StringBuffer.html) is thread-safe,
which is rarely needed.

#### ScheduledExecutorService over Timer
Drawing from [Java Concurrency in Practice](#recommended-reading) (directly borrowed from
a stackoverflow
[question](http://stackoverflow.com/questions/409932/java-timer-vs-executorservice)).

- `Timer` can be sensitive to changes in the system clock, `ScheduledThreadPoolExecutor` is not

- `Timer` has only one execution thread, so long-running task can delay other tasks.

- `ScheduledThreadPoolExecutor` can be configured with multiple threads and a `ThreadFactory`<br />
  *See [manage threads properly](#manage-threads-properly)*

- Exceptions thrown in `TimerTask` kill the thread, rendering the `Timer` ineffective.

- ThreadPoolExecutor provides `afterExceute` so you can explicitly handle execution results.

#### List over Vector
`Vector` is synchronized, which is often unneeded.  When synchronization is desirable, a [synchronized list](http://docs.oracle.com/javase/7/docs/api/java/util/Collections.html#synchronizedList(java.util.List)) can usually serve as a drop-in replacement for `Vector`.

### equals() and hashCode()
If you override one, you must implement both.
*See the equals/hashCode
[contract](http://docs.oracle.com/javase/7/docs/api/java/lang/Object.html#hashCode())*

`Objects.equal()` and `Objects.hashCode()`
make it very easy to follow these contracts.

### Premature optimization is the root of all evil.
Donald Knuth is a smart guy, and he had a few things to [say](http://c2.com/cgi/wiki?PrematureOptimization) on the topic.

Unless you have strong evidence that an optimization is necessary, it's usually best to implement the un-optimized version first (possibly leaving notes about where optimizations could be made).

So before you spend a week writing your memory-mapped compressed huffman-encoded hashmap, use the stock stuff first and *measure*.

### TODOs

#### TODOs should not reach production code
While working on a feature you can use TODOs as a tool to make sure you didn't forget anything before you make a pull request. They are not a way to manage tasks in the project! We have a dedicated issue tracker for this.

#### Open issues on uncompleted TODOs
If it is something that can be very disturbing to the code reader, one can comment with the issue number to make sure no duplicate will be opened. It will also serve as a constant reminder that something needs to be fixed.

    
    // Bad.
    //   - TODO is in production code.
    // TODO: Implement request backoff.

    // Good.
    // ISSUE #5794: Implement request backoff.


### Obey the Law of Demeter ([LoD](http://en.wikipedia.org/wiki/Law_of_Demeter))
The Law of Demeter is most obviously violated by breaking the
[one dot rule](http://en.wikipedia.org/wiki/Law_of_Demeter#In_object-oriented_programming), but
there are other code structures that lead to violations of the spirit of the law.

#### In classes
Take what you need, nothing more.  This often relates to [texas constructors](#stay-out-of-texas) but it can also hide in constructors or methods that take few parameters.  The key idea is to defer assembly to the layers of the code that know enough to assemble and instead just take the minimal interface you need to get your work done.

    
    // Bad.
    //   - Weigher uses hosts and port only to immediately construct another object.
    class Weigher {
      private final double defaultInitialRate;

      Weigher(Iterable<String> hosts, int port, double defaultInitialRate) {
        this.defaultInitialRate = validateRate(defaultInitialRate);
        this.weightingService = createWeightingServiceClient(hosts, port);
      }
    }

    // Good.
    class Weigher {
      private final double defaultInitialRate;

      Weigher(WeightingService weightingService, double defaultInitialRate) {
        this.defaultInitialRate = validateRate(defaultInitialRate);
        this.weightingService = checkNotNull(weightingService);
      }
    }

If you want to provide a convenience constructor, a factory method or an external factory in the form of a builder you still can, but by making the fundamental constructor of a Weigher only take the things it actually uses it becomes easier to unit-test and adapt as the system involves.

#### In methods
If a method has multiple isolated blocks consider naming these blocks by extracting them to helper methods that do just one thing.  Besides making the calling sites read less
like code and more like english, the extracted sites are often easier to flow-analyse for human eyes. The classic case is branched variable assignment.  In the extreme, never do this:

    
    void calculate(Subject subject) {
      double weight;
      if (useWeightingService(subject)) {
        try {
          weight = weightingService.weight(subject.id);
        } catch (RemoteException e) {
          throw new LayerSpecificException("Failed to look up weight for " + subject, e)
        }
      } else {
        weight = defaultInitialRate * (1 + onlineLearnedBoost);
      }

      // Use weight here for further calculations
    }

Instead do this:

    
    void calculate(Subject subject) {
      double weight = calculateWeight(subject);

      // Use weight here for further calculations
    }

    private double calculateWeight(Subject subject) throws LayerSpecificException {
      if (useWeightingService(subject)) {
        return fetchSubjectWeight(subject.id)
      } else {
        return currentDefaultRate();
      }
    }

    private double fetchSubjectWeight(long subjectId) {
      try {
        return weightingService.weight(subjectId);
      } catch (RemoteException e) {
        throw new LayerSpecificException("Failed to look up weight for " + subject, e)
      }
    }

    private double currentDefaultRate() {
      defaultInitialRate * (1 + onlineLearnedBoost);
    }

A code reader that generally trusts methods do what they say can scan calculate quickly now and drill down only to those methods where I want to learn more.

### Don't Repeat Yourself ([DRY](http://en.wikipedia.org/wiki/Don't_repeat_yourself))
For a more long-winded discussion on this topic, read
[here](http://c2.com/cgi/wiki?DontRepeatYourself).

#### Extract constants whenever it makes sense

#### Centralize duplicate logic in utility functions

### Manage threads properly
When spawning a thread, either directly or with a thread pool, you need to take special care that you properly manage the lifecycle.  Please familiarize yourself with the concept
of daemon and non-daemon threads (and their effect on the JVM lifecycle) by reading the
documentation for [Thread](http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html). Failing to understand these concepts can cause your application to hang at shutdown.

Shutting down an [ExecutorService](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html) properly is a slightly tricky process (see javadoc).
If your code manages an executor service with non-daemon threads, you need to perform
[ExecutorService#Shutdown](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html#shutdown()).

If you want to automatically perform cleanup like this when the VM is shutting down, consider registering with [ShutdownRegistry](https://github.com/twitter/commons/blob/master/src/java/com/twitter/common/application/ShutdownRegistry.java).

### Avoid unnecessary code
#### Superfluous temporary variables.

    
    // Bad.
    //   - The variable is immediately returned, and just serves to clutter the code.
    List<String> strings = fetchStrings();
    return strings;

    // Good.
    return fetchStrings();

#### Unneeded assignment.

    
    // Bad.
    //   - The "Default" value is never realized.
    String value = "Default";
    try {
      value = "The value is " + parse(foo);
    } catch (BadException e) {
      throw new IllegalStateException(e);
    }

    // Good
    String value;
    try {
      value = "The value is " + parse(foo);
    } catch (BadException e) {
      throw new IllegalStateException(e);
    }

### The 'fast' implementation
Don't bewilder your API users with a 'fast' or 'optimized' implementation of a method.

    
    int fastAdd(Iterable<Integer> ints);

    // Why would the caller ever use this when there's a 'fast' add?
    int add(Iterable<Integer> ints);
