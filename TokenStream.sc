InputStream {
	var <>pos = 0, routine, inputString;
	
	*new { arg input;
		^super.new.init(input)
	}
	init { arg input;
		inputString = input;
		routine = Routine {
			input.do(_.yield)
		}
		^this
	}
	next {
		var ch = routine.next;
		pos = pos + 1;
		^ch;
	}
	peek {
		var char = inputString[pos];
		^""++char;
	}
	eof {
		^(inputString[pos] == nil)

	}
	croak { arg msg;
		Error(format("RhythmCompiler error: % (pos: %).", msg, pos)).throw;
	}
}

TokenStream {
	// turns an input string into a stream of tokens which the Parser consumes
	var current;
	var alpha_chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
	var punctuation = "[]";
	var binop = "%!*/|?";
	var unop = ":";
	var digit = "0123456789";
	var input;

	*new { arg inputString;
		^super.new.init(inputString)
	}

	init { arg inputString;
		current = ();
		input = InputStream(inputString);
		^this
	}

	// consume stream while predicate is true
	// predicate is function that returns bool eg if char is whitespace
	readWhile { arg predicate;
		var str = "";
		while ({input.eof.not && predicate.value(input.peek)}, {
			str = str ++ input.next;
			});
		^str;
	}

	readNote {
		var noteName = input.peek, tail, value;
		input.next;
		tail = this.readWhile({ arg ch; ch == "-" });
		value = noteName ++ tail;
		^(type: 'note', val: noteName++value.size)
	}

	readRest {
		var rest = this.readWhile({ arg ch; ch == "-" });
		^(type: 'rest', val: rest.size);
	}

	readPunctuation {
		^(type: 'punc', val: input.next)
	}

	readBinOp {
		^(type: 'binop', val: input.next, left: nil, right: nil)
	}

	readUnOp {
		^(type: 'unop', val: input.next, left: nil)
	}

	readNumber {
		var numberString = this.readWhile({ arg ch; (digit++".").contains(ch)});

		^(type: 'num', val: numberString.asFloat)
	}

	parseChar { arg ch;
		var res = case
			{ alpha_chars.contains(ch) } { this.readNote }
			{ ch == "-" } { this.readRest }
			{ punctuation.contains(ch) } { this.readPunctuation }
			{ binop.contains(ch) } { this.readBinOp }
			{ digit.contains(ch) } { this.readNumber }
			{ unop.contains(ch) } { this.readUnOp };

		res ?? { this.croak(format("unrecognized character %", ch)) }
		^res;
	}

	readNext {
		var ch;
		this.readWhile({ arg c; " \t\n".contains(c) });
		ch = input.peek;
		if(input.eof) {
			^nil
		} {
			^this.parseChar(ch)
		}
	}

	next {
		current = this.readNext;
		^current
	}

	peek {
		^current
	}

	eof {
		^(this.peek == nil)
	}

	collectRemaining { arg fn = { arg item; item };
		var arr = [];
		while({this.eof.not}, {
			var item = this.peek;
			if (item != ()) {
				arr = arr.add(fn.value(this.peek));	
			};
			this.next;
			});
		^arr
	}

	croak { arg msg;
		input.croak(msg);
	}
}


Parser {
	// generates AST from token stream
	var input;
	var <>tree;
	var continue;

	*new { arg inputString;
		^super.new.init(inputString);
	}

	*fromTokenStream { arg inputStream;
		^super.new.initFromTokenStream(inputStream);
	}

	init { arg inputString;
		input = TokenStream(inputString);
		tree = (type: 'tree', val: []);
		continue = true;
		this.fillTree;
		^tree
	}

	initFromTokenStream { arg inputStream, parentNode;
		input = inputStream;
		tree = (type: 'tree', val: []);
		continue = true;
		^this.fillTree;
	}

	fillTree {
		while({ input.eof.not && this.continueNode }, {
			var token = input.peek;
			input.next;
			if (token != ()) {
				this.parseToken(token);	
			};
		})
	}

	parseToken { arg token;
		switch(token.type,
			'rest', { this.parseRest(token) },
			'note', { this.parseNote(token) },
			'punc', { this.parsePunc(token) },
			'binop', { this.parseBinOp(token) },
			'unop', { this.parseUnOp(token) },
 			'num', { this.parseNum(token) }
		);
	}

	parseRest { arg token;
		this.addNode(token);
	}

	parseNote { arg token;
		this.addNode(token);
	}

	parsePunc { arg token;
		var val = "" ++ token.val;
		switch(val, 
			"[", {
				var newNode = Parser.fromTokenStream(input).tree;
				this.addNode(newNode);
				}, 
			"]", { continue = false }
			);
	}

	parseBinOp { arg token;
		var left, right;
		var lastIndex = tree.val.size - 1;
		
		right = input.peek;
		left = tree.val[lastIndex];
		
		// handle errors binop errors
		case
			{ right.notNil && right.type != 'num' } {
				input.croak(format("right side of % must be a number", token.val))
				}
			{ right.isNil } {
				input.croak(format("% is a binary operator, must have a right side", token.val))
			};
		
		token.right = right;
		if ( (left.type == 'note') || (left.type == 'rest' ) ) {
			var tr;
			left = tree;
			token.left = left;
			tr = (type: 'tree', val: [token]);
			tree = tr;
		} {
			token.left = left;
			tree.val[lastIndex] = token;
		};
		
	}

	parseUnOp { arg token;
		var left;
		var lastIndex = tree.val.size - 1;
		left = tree.val[lastIndex];

		if ( (left.type == 'note') || (left.type == 'rest') ) {
			var tr;
			left = tree;
			token.left = left;
			tr = (type: 'tree', val: [token]);
			tree = tr;
		} {
			token.left = left;
			tree.val[lastIndex] = token;
		};
	}

	parseNum { arg token;
		// this.addNode(token);
	}

	addNode { arg node;
		tree.val = tree.val.add(node);
	}
	continueNode {
		^continue
	}

}

Compile {
	var lookupDict;
	var tickValue;
	var result;

	// returns a routine from input string 
	*new { arg inputString, repeats = inf, lookup = currentEnvironment, tick = 1;
		// these defaults are useful
		^super.new.init(inputString, repeats, lookup, tick)
	}

	init { arg inputString, repeats, lookup, tick;
		var tree;
		lookupDict = lookup ? ();
		tickValue = tick;
		tree = Parser(inputString);

		result = this.processNode(tree).flat;
		result.postln;
		// result should be a (flat) array of functions that return embeddable objects

		^Routine { arg inval;
			repeats.do {
				result.do { arg item;
					// embedNode must eventually return <Routine or Event>.embedInStream or <Object>.yield
					inval = item.embedInStream;		
				}
			}
		}
	}

	processNode { arg node;
		var res = switch(node.type,
			'tree',  { this.processTree(node)  },
			'binop', { this.processBinOp(node) },
			'unop',  { this.processUnOp(node)  },
			'note',  { this.processNote(node)  },
			'rest',  { this.processRest(node)  },
			'num',   { this.processNum(node)   }
			);
		^res
	}

	processTree { arg node;
		^node.val.collect({ arg item, i;
			this.processNode(item);
			});
	}

	processBinOp { arg binop;
		var left, right, res, val;
		
		left = this.processNode(binop.left);
		// right should always be a number, otherwise parser will have croaked an error
		right = this.processNum(binop.right);		
		val = "" ++ binop.val;
			
		res = switch(val, 
			/* the following binops can have a flat lhs */
			"%", { this.padTo(left.flat, right)     },
			"!", { this.duplicate(left.flat, right) },
			"*", { this.multiply(left.flat, right)  },
			"/", { this.divide(left.flat, right)    },
			"|", { this.truncate(left.flat, right)  }
			/* if you define any binops that shuffle the order of nodes
			 * in lhs then lhs shouldn't be flattened
			 */
			);
		^res
	}

	// methods that transform the lhs of a binop

	// %
	padTo { arg left, right;
		var total = left.collect(_.dur).sum;
		var remainder = total % right;
		left = left.add((dur: right - remainder, val: nil));
		^left
	}

	// |
	truncate { arg left, right;
		var total = left.collect(_.dur).sum;
		var remainder = total % right;
		var index = left.size - 1;
		var item;

		if (total <= right) {
			left = left.add((dur: right - remainder, val: nil))			
		} {
			while( { (remainder > 0) && (index > 0) }, {
				item = left[index];
				if ( remainder < item.dur ) {
						// item.dur = item.dur - remainder;
						item.dur = item.dur - remainder;
						remainder = 0;
					} {
						left.removeAt(index);
						remainder = remainder - item.dur;
					}; 
				index = index - 1
			});			
		};
		^left
	}

	// !
	duplicate { arg left, right;
		^left.dup(right)
	}

	// *
	multiply { arg left, right;
		^left.collect(_.multiplyBy(right));
	}

	// /
	divide { arg left, right;
		if (right == 0) {
			^Error("division by zero error").throw
		} {
			^this.multiply(left, right.reciprocal)	
		}
	}

	processUnOp { arg unop;
		var left = this.processNode(unop.left);
		var val = "" ++ unop.val;
		var res;

		res = switch(val,
			":", { this.scramble(left) });
		^res
	}

	// methods that process lhs of unary op

	// :
	scramble { arg left;
		var total = left.collect(_.dur).sum;
		^(
			type: 'tree',
			dur: total,
			val: left,
			multiplyBy: { arg ev, multiplier;
				var newTotal = 0;
				ev.val = ev.val.collect({ arg item;
					item.multiplyBy(multiplier);
					newTotal = newTotal + item.dur;
					item;
					});
				ev.dur = newTotal;
				ev;
			},
			embedInStream: { arg ev, inEvent;
				Routine({ arg inval;
					var timeLeft = ev.dur;
					ev.val.scramble.do { arg item;

						if ( timeLeft >= ev.dur ) {
							inval = item.embedInStream;
							} {
								item.dur = timeLeft;
								inval = item.embedInStream;
							};
					}
				}).embedInStream(inEvent);
			}
		)
	}
	
	// ---------------------------------------------------------------------
	processNote { arg node;
		var name = node.val[0].asSymbol;
		var dur = "" ++ node.val[1..]; 
		var lookup = lookupDict;
		^(dur: dur.asFloat * tickValue, val: name, type: 'note',
			multiplyBy: { arg ev, multiplier; ev.dur = ev.dur * multiplier; ev; },
			embedInStream: { arg ev, inEvent;
				var event = (dur: ev.dur, val: ev.val, type: ev.type);
				// rest can remain 'static'
				// useful to override embedInStream for notes because
				// lookup dict / environment can change after compilation
				event.putAll(name.lookupIn(lookup));
				event.embedInStream(inEvent)
				});

	}

	processRest { arg node;
		^(dur: node.val * tickValue, val: nil, type: 'rest',
			multiplyBy: { arg ev, multiplier; ev.dur = ev.dur * multiplier; ev; },
			embedInStream: { arg ev, inEvent;
				(dur: ev.dur, type: 'rest').embedInStream(inEvent)
				}
		)	
	}

	processNum { arg node;
		^node.val
	}

}
