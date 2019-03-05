```
d = Dictionary.with(*[
	\x -> (buf: "buffer x"),
	\s -> (buf: "buffer s")
	]);

c = Compile("x--s--x-", repeats: inf, fn: _.lookupIn(d));

Pbindf(c, \instrument, \sampleSynth).trace.play;
```

Compile's fn arg is used to map the alphabetical tokens in the rhythm string you want to compile to events  
for example you might want to use a sample player that has a 'buf' argument, so you can   gather some different events with the buffers into a kind of 'drum machine' dictionary d, and look them up in it.  
Compile returns a Routine that yields events so you can easily plug it into a pbind, or use it inside a Task or other Routine.

### syntax guide:

`"x---"` this is a note that lasts 4 'ticks' (ticks are deliberately kept vague)  
brackets: `"[x--x--]"` brackets group things together for binops / unops  
spaces: `"x--- x--- x--- x---"` spaces are ignored so this is just a 4/4 beat  
dashes: `"-- -- -- x-"` this is 3 rests then a beat  


#### binary operators:
* `%` : `"[x--x--]%4"` pads the lhs to next multiple of the rhs
	so this will be transformed to `"x--x-- --"` (8 ticks in total)

* `|` : truncates the lhs so `"[x--x--]|4" -> "x--x"`

* `!` : duplicate lhs `"x--x-!3" --> "x--x- x--x- x--x-"`

* `*` : multiply -- `"x--- x--- * 1.5" --> "x----- x-----"`
* `/` : does what you might think

#### unary operators:
* `:` scrambles the lhs and embeds it in the routine (it will scramble it again each time if repeated)


I recommend to use myLib for useful extensions to the Symbol class, and for using modules with Import  
* [myLib](https://github.com/Endut/myLib)
* [example modules](https://github.com/Endut/_modules)