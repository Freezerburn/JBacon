JBacon
======

A Functional Reactive Programming library inspired by Bacon.js (https://github.com/raimohanska/bacon.js).
And by "inspired by", what I actually mean is "ported pretty much directly without DOM interaction,
because Java doesn't interact with the DOM".

# API Reference
## JBacon.java
### Overview
A utility class that is used to create a myriad of types of ```EventStream```s, ranging from an ```EventStream```
that immediately ends to an ```EventStream``` that pushes values at regular intervals.

Please note that if you use JBacon, in order for your program to shut down properly, a ```System.exit``` call
must be made for shutdown hooks JBacon creates upon program startup. This cleanly stops concurrent ```EventStream```
tasks from running, thus allowing the program to actually stop.
### Methods
####```<T> EventStream<T> once(T val)```  
Creates an ```EventStream``` that emits the passed value ```val``` a single time, and then promptly ends.
The val will only be emitted when something subscribes either to the returned ```EventStream```, or to any
new ```EventStream```s made by calling methods such as ```EventStream.map```.  

Method is generic so that any type of ```EventStream``` can be created. The returned ```EventStream``` will emit two 
things: ```Event.Initial<T>(val)``` and ```Event.End```. No further values will be emitted.

####```<T> EventStream<T> fromArray(T... vals)```
Creates an ```EventStream``` that immediately emits the values passed to the ```fromArray``` method to the first
subscriber, then promptly ends. Please note that there is no delay between emitting each value, please see
```JBacon.sequentially``` for that functionality.

NOTE: As of Jan 27, 2013 there is a bug in the implementation where the passed values can be emitted in
a different order than passed to ```fromArray```. This is due to threads being run in different orders. A good
way to synchronize the ```EventStream``` so as to emit the values in the correct order every time is needed.

// TODO: Write documentation for the rest of the JBacon methods/constants

## Observable.java
// TODO: Write documentation
## EventStream.java
// TODO: Write documentation
## Property.java
// TODO: Write documentation
## Bus.java
// TODO: Write documentation
## Event.java
// TODO: Write documentation
## Streamable.java
// TODO: Write documentation
## F.java / F1.java / F2.java
// TODO: Write documentation
## Promise.java
// TODO: Write documentation

License (also in LICENSE)
=========================
Copyright (c) 2013 "Vincent Kuyatt"

Permission is hereby granted, free of charge, to any person obtaining a copy of this software
and associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
