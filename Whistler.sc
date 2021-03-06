/*
    whistling.sc - Whistling for Weavrs
    Copyright (c) 2011 Philter Phactory Limited
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/


Whistler {

	var serveroptions, envirsoundsArray, envirsound, envirbuffer;
	var python, trackID, trackname;
	var <>renderMode = true;

	*new {
		
		^super.new.initWhistler;
	
	}

	initWhistler { 	

		"... init Whistler class ...".postln;
		this.setServerOptions;
		this.addSynthDefs;
		this.setupOSC;

	}

	setupOSC {

		"... setting up OSC ...".postln;
		python = NetAddr("127.0.0.1", 57000); // python listens to OSC on port 57000
		// the BeatBoxer class will implement '/render_beatbox'
		OSCresponderNode(nil, '/render_whistle', { |t, r, msg|
			this.compose(msg[1], msg[2], msg[3], msg[4], msg[5], msg[6], msg[7..msg.size]);
		}).add;

	}

	setServerOptions {

		"... setting server options ...".postln;
		Server.default.recSampleFormat_("int24");
		serveroptions = Server.default.options;
		serveroptions.numOutputBusChannels = 2; 
		serveroptions.sampleRate = 44100; 
		serveroptions.verbosity = -1; 

	}

	compose { arg trackID, genderarg, agearg, emotionarg, timearg, numwhistlestodayarg, searchwordsarg, groove=true;

		var gender, age, emotion, time, numwhistlestoday, searchwords;
		var scales, scale, notes, durations, sustain;
		var scorepattern,  envirpattern, mainpattern, mixerpattern, notepatterns, durpatterns, sustainpatterns;
		var tempo, trackduration; 
		var direction, from, to;

		"------- new composition @ ".post; Date.localtime.post; " -------".postln; // info to keep in the logs of the Weavr audio server
		("--> trackID :" + trackID).postln;
		("--> gender :" + genderarg).postln;
		("--> age :" + agearg).postln;
		("--> emotion :" + emotionarg).postln;
		("--> time :" + timearg).postln;
		("--> numwhistlestoday :" + numwhistlestodayarg).postln;
		("--> searchwords :" + searchwordsarg).postln;

		searchwords = if((searchwordsarg.size==0) || (searchwordsarg==nil), {["xylophone", "voices", "new york"]}, { searchwordsarg });
		gender = genderarg ? 2; // male (1), object (2) and female (3)
		numwhistlestoday = 6.min(numwhistlestodayarg ? 1); // the number of whistles until now/today, but capping at 3
		//numwhistlestoday = if( numwhistlestoday > 3, { 3 }, { numwhistlestoday });
		age = agearg ? 96; // max 120 years
		time = timearg ? 12; // time is from 0 to 24
		emotion = if((emotionarg == nil) || (emotionarg.asString == ""), { "funky" }, { emotionarg.asString });

		trackname = trackID.asString++".aif";
		searchwords = searchwords.collect({arg symbol; symbol.asString}); // if python is sending a symbol
		
		direction = if(emotion.size.even, {-1}, {1});
		from = age.linlin(0, 120, 0.95, 0.1) * direction;
		to = age.linlin(0, 120, -0.1, -0.95) * direction;
		
		tempo = if(time<7, {23}, {time}).linlin(7, 23, 1.9, 0.9); // (at 7am bots are upbeat, around midnight slow)
		TempoClock.default.tempo = tempo;
		
		envirsound = envirsoundsArray.choose;
		
		scales = [ Scale.ritusen, Scale.kumoi, Scale.hirajoshi, Scale.iwato, Scale.chinese,
				Scale.indian, Scale.pelog, Scale.prometheus, Scale.scriabin, Scale.jiao, 
				Scale.spanish, Scale.whole, Scale.locrian, Scale.augmented, Scale.augmented2, 
				Scale.hexMajor7, Scale.shang, Scale.hexDorian, Scale.todi, Scale.hexPhrygian, 
				Scale.hexSus, Scale.hexMajor6, Scale.major, Scale.bhairav, Scale.ionian, 
				Scale.dorian, Scale.phrygian, Scale.lydian, Scale.mixolydian, Scale.leadingWhole, 
				Scale.aeolian, Scale.egyptian, Scale.minor, Scale.harmonicMinor,
				Scale.harmonicMajor, Scale.yu, Scale.melodicMinor, Scale.melodicMinorDesc, 
				Scale.melodicMajor, Scale.bartok, Scale.hexAeolian, Scale.hindu, Scale.purvi, 
				Scale.ahirbhairav, Scale.hungarianMinor, Scale.superLocrian, Scale.romanianMinor, 
				Scale.zhi, Scale.neapolitanMinor, Scale.enigmatic, Scale.gong, Scale.lydianMinor, 
				Scale.neapolitanMajor, Scale.locrianMajor, Scale.marva, Scale.diminished ];
		
		scale = scales[((emotion.size+time)%scales.size-1)].degrees++12; // picking scales from emotion word size and time of day

		// --------- FORM: using search word values to create a musical form (e.g., ABACA) ---------
		
		searchwords = searchwords.insert(if(searchwords[0][0].ascii.even, {2}, {3}), searchwords[0]);
		searchwords = searchwords.insert(if(searchwords[1][0].ascii < 110, {3}, {searchwords.size}), searchwords[1]);
				
		// --------- NOTES: ascii value turned into nearest notes in a scale ---------
		
		notes = searchwords.ascii.collect({ arg asciiwordarray, i;
				asciiwordarray.collect({ arg char;
					scale[scale.indexIn((char-searchwords.ascii[0][0])%12)];
				}) ++ '\rest';
			});
		
		if(groove, {	// GROOVE !!!
			// --------- DURATIONS: vowels/consonants turned into note durations ---------

			durations = searchwords.collect({arg word;
					word.separate.collect({arg char;
						if(char[0].isVowel, {
							if(char[0].ascii<90, {1+((tempo.reciprocal/4).rand)}, 
								{if(char[0].ascii<120, {0.67+((tempo.reciprocal/6).rand)}, 
									{0.5+((tempo.reciprocal/6).rand)}) } 
							)}, 
							{if(char[0].ascii<90, {1+((tempo.reciprocal/4).rand)}, 
								{if(char[0].ascii<120, {1.5+((tempo.reciprocal/6).rand)}, 
									{0.5+((tempo.reciprocal/6).rand)}) }) 
						});
					}) ++ (word.size/10).round(0.5); // silence between each word depends on its length
				});
				
			// --------- SUSTAIN: vowels/consonants turned into note sustain ---------
		
			sustain = searchwords.collect({arg word;
					word.separate.collect({arg char;
						if(char[0].isVowel, {
							if(char[0].ascii<90, {0.9-((tempo.reciprocal/4).rand)}, 
								{if(char[0].ascii<120, {0.57-((tempo.reciprocal/6).rand)}, 
								{0.4-((tempo.reciprocal/6).rand)}) } 
							)}, 
							{if(char[0].ascii<90, {0.9-((tempo.reciprocal/4).rand)}, 
								{if(char[0].ascii<120, {1.4-((tempo.reciprocal/6).rand)}, 
									{0.4-((tempo.reciprocal/6).rand)}) }) });
					}) ++ (word.size/10); // the sustain is a little shorter than the note
				});


		},{
			// --------- DURATIONS: vowels/consonants turned into note durations ---------
			
			durations = searchwords.collect({arg word;
					word.separate.collect({arg char;
						if(char[0].isVowel, {if(char[0].ascii<112, {1}, {0.5})}, {if(char[0].ascii<112, {0.5}, {0.25}) });
					}) ++ (word.size/15).round(0.25); // silence between each word depends on its length
				});
				
			// --------- SUSTAIN: vowels/consonants turned into note sustain ---------
		
			sustain = searchwords.collect({arg word;
					word.separate.collect({arg char;
						if(char[0].isVowel, {if(char[0].ascii<112, {0.8}, {0.4})}, {if(char[0].ascii<112, {0.4}, {0.25}) });
					}) ++ (word.size/10)-0.1; // the sustain is a little shorter than the note
				});
		});

		// --------- SET PITCH: (males tonic = 76, objects = 79, females = 82) ---------

		notes = notes + switch(gender) {1} {76+numwhistlestoday} {2} {79+numwhistlestoday} {3} {82+numwhistlestoday};
		
		// --------- MAKE PATTERN: vowels/consonants turned into note durations ---------
		
		notepatterns = notes.collect({ arg array; Pseq(array, 1) });
		durpatterns = durations.collect({ arg array; Pseq(array, 1) });
		sustainpatterns = sustain.collect({ arg array; Pseq(array, 1) });
		trackduration = durations.flatten.sum*tempo.reciprocal;

		scorepattern = Pbind(\instrument, 	\whistler,
						   \midinote, 	Pseq(notepatterns, 1), 
						   \dur,  		Pseq(durpatterns, 1),
						   \sustain,  	Pseq(sustainpatterns, 1),
						   \noiseamp, 	age.linexp(1, 120, 0.2, 0.6),
						   \amp, 			1
					 );
		
		envirpattern = Pbind(\instrument, 	\wsampler, 
							\bufnum, 		envirbuffer.bufnum, 
							\startPos, 	(44100*60*2).rand, // select a random frame in the first 2 minutes
							\amp, 		1, 
							\dur, 		Pseq([trackduration], 1)
					  );

		// future robots will use more info (location, environment, weather) to control environment (space)
		// mainpattern = Pfx(envirpattern, \whistlerspace, 
		mainpattern = Pfx(scorepattern, \whistlerspace, 
					    	\mix, 	0.2, 
						\rtime, 	0.2,  
						\damp, 	0.2, 
						\time, 	trackduration, 
						\fromA, 	from, 
						\toB, 	to
					);
		
		mixerpattern = Ppar([ envirpattern, mainpattern ], 1);
		
		// --------- EITHER RENDER or PLAY (in dev mode)  ---------

		if(renderMode.not, { // if in development mode
			scorepattern.asCompileString.postln;
			("--> Trackduration :" + trackduration).postln;
			("--> Duration Sum :" + durations.flatten.sum).postln;
			mixerpattern.play;
		}, {
			this.render(mixerpattern, durations.flatten.sum+0.2); // renderdurations are different from track dur (due to TempoClock)
		});

	}

	render {arg pattern, renderduration;
		var whistlescore, renderFunc;

		var finishedRenderAction;
		
		finishedRenderAction = {
			"Finished rendering track: ".post; trackname.postln;
			"----------------------------------------------------------------".postln;
			"----------------------------------------------------------------".postln;
			{ python.sendMsg('/rendered_whistle', trackID, trackname) }.defer(1);
		};

/*		
		pattern.render(
			("~/"++trackname).standardizePath, 
			renderduration, 
			sampleFormat: "int16", 
			options:serveroptions
		);
*/

		whistlescore = pattern.asScore(renderduration);
		whistlescore.score = whistlescore.score.addFirst([0.0, envirbuffer.allocReadMsg(envirsound)]);
		whistlescore.recordNRT("whistle-oscFile", 
							outputFilePath: ("~/"++trackname).standardizePath,
							sampleRate: 22050,
							sampleFormat: "int16", 
							options: serveroptions,
							duration: renderduration,
							completionString: "; rm whistle-oscFile",
							action: {finishedRenderAction.value} // only works on > SC 3.4
						);
	
		// { python.sendMsg('/rendered_whistle', trackID, trackname) }.defer(2); // SC 3.4 code - wait 2 secs and send to Python.

	}

	addSynthDefs {

		"... adding synthdefs ...".postln;
		envirsoundsArray =  (Platform.userAppSupportDir +/+  "sounds/whistling/*").pathMatch; // the background sounds 
		envirsound = envirsoundsArray.choose; // choose one
		envirbuffer = Buffer.read(Server.default, envirsound); // and load into buffer if used in realtime mode
		
		// version 11 of whistling synthdef (blown filter limiters put in place - especially since Nyquist is 11025 Hz - and amp added):
		
		SynthDef(\whistler, {arg freq=440, amp=0.1, gate=1, noiseamp=0.3, pureamp=1, cutoff=5, attacknoise = 0.6, guttnoise=0.15, vibrato=1.3, pitchslide=0.09;
			var signal, unienv;
			var harmonics, noisesource;
			var onset, onsetenv;
			
			// -- frequency vibrato and pitch correction
			freq = Line.ar(freq * Rand(1-pitchslide, 1+pitchslide), freq, 0.24); // these vars are good to turn into args
			freq = freq * SinOsc.ar(vibrato * Rand(0.5, 5), mul: Rand(0.005, 0.01), add:1);
			
			// -- guttural noise onset in whistle
			onset = RLPF.ar(PinkNoise.ar(guttnoise * Rand(0.06, 0.5)), 200+(freq*0.0625), 0.99);
			//onset = LPF.ar(PinkNoise.ar(0.15), freq*0.125);
			onsetenv = EnvGen.ar(Env.perc(0.01, 0.2));
		
			// -- the harmonic spectrum of the whistle, letting through noise as well, although the saw takes care of the harmonics
			harmonics = DynKlank.ar(`[
						[freq, freq*2, 18000.min(freq*3), 18000.min(freq*4)], // make sure filters are not blown 
						[0.8, 0.15, 0.09, 0.07] * LFNoise2.ar(2, 0.5, 0.5), 
						[0.9, 0.4, 0.1, 0.03 ]], 
						Saw.ar(freq, 0.001 * pureamp * EnvGen.ar(Env.adsr(Rand(0.0001, 0.2), Rand(0.2, 0.5), Rand(0.4, 0.8), Rand(0.1, 0.2))))
						+
						PinkNoise.ar(0.03 * noiseamp * EnvGen.ar(Env.adsr(Rand(0.0001, 0.2), Rand(0.2, 0.5), Rand(0.4, 0.8), Rand(0.1, 0.2)))));
						
			noisesource = 
					BPF.ar(BrownNoise.ar(0.5 * noiseamp * EnvGen.ar(Env.adsr(Rand(0.0001, 0.2), Rand(0.2, 0.5), Rand(0.4, 0.8), Rand(0.1, 0.2))) ), freq, 0.1)
					+ 
					BPF.ar(WhiteNoise.ar(0.4 * noiseamp * attacknoise * EnvGen.ar(Env.perc(0.1, 0.6))), freq, 0.1);
					
			signal = LPF.ar(harmonics + noisesource, 18200.min(freq * cutoff) * EnvGen.ar(Env.adsr(Rand(0.0001, 0.2), Rand(0.2, 2.5), Rand(0.4, 0.8), Rand(0.1, 0.2))));
			signal = LPF.ar(signal, 1000); // make sure high pitches loose the harmonics (become too edgy up there)
			unienv = EnvGen.ar(Env.asr(0.0001, 1, 0.2), gate, doneAction:2); // reverb time?
			signal = (signal * unienv) + (onset * onsetenv);
			Out.ar(0, Limiter.ar(LeakDC.ar(signal*(3.9*amp))));
		}).store;
		
		SynthDef(\wsampler, {arg out=0, bufnum = 0, amp=0.9, startPos=0, dur=14, gate=1;
			var sound, pan;
			 // rate 2, since rendering sample rate is 22050, but source soundfiles are 44.1 K
			 // changed (in helsinki 16 May 2012) sample rate to 44100
			sound = PlayBuf.ar(2, bufnum, 1, startPos: startPos, loop: 1) * amp;
			//sound = PlayBuf.ar(1, bufnum, 1, startPos: startPos, loop: 1) * amp;   // source files are stereo
			pan = sound * EnvGen.kr(Env.linen(0.2, dur-0.4, 0.2), gate, doneAction: 2);
			// pan = Pan2.ar( sound * EnvGen.kr(Env.linen(0.2, dur, 0.2), gate, doneAction: 2), 0);
			Out.ar(0, pan);
		}).store;
				
		SynthDef(\whistlerspace, { arg mix=0.2, rtime=0.1, damp=0.1, speed=2, time = 14, fromA= -0.8, toB=0.8 ;
			var in, ampsig, reverbsig, pansig;
			in = In.ar(0, 1);
			ampsig = EnvGen.ar(Env.linen(speed*0.5, time-speed, speed*0.5, 1), doneAction:2);
			reverbsig = FreeVerb.ar(in * ampsig, mix, rtime, damp);
			pansig = Pan2.ar(reverbsig, Line.kr(fromA, toB, time));
			XOut.ar(0, 1, pansig);
		}, #[0.1, 0.1, 0.1, 0.1, 0.1, 0.1]).store;

	}
}
