package com.thriple.define.engine

/**
 * Plain-language instructions (no gym jargon on screen): what one "time" of the movement is, how to do it,
 * the pace, one thing to avoid, and an easier option where it helps.
 */
data class Guide(val oneTime: String, val steps: List<String>, val pace: String, val avoid: String, val easier: String? = null)

object Coaching {
    val primerTitle = "HOW A WORKOUT WORKS"
    val primer = listOf(
        "Each exercise is one movement that you repeat a few times in a row.",
        "Doing the movement once, all the way there and back, counts as 1. Tap the big ring each time you finish one.",
        "The screen tells you how many in a row, like \"do it 6 times\". Doing those 6 without stopping is one round.",
        "After a round you rest for a minute. The app counts it down. Most exercises are 3 rounds.",
        "Follow the little figure's pace: slow on the way down, steady on the way up.",
        "If you can't finish a round, tap \"Can't finish this round\". The plan adjusts. Stop if anything feels sharp or painful.",
    )

    val guides: Map<String, Guide> = mapOf(
        "pushups" to Guide(
            oneTime = "Lower your chest until it's about a fist above the floor, then push back up until your arms are straight. That's 1.",
            steps = listOf(
                "Hands on the floor a bit wider than your shoulders, arms straight.",
                "Step your feet back so your body is one straight line from head to heels.",
                "Bend your elbows and lower your chest, elbows angled back toward your feet (not straight out to the sides).",
                "Push the floor away until your arms are straight again.",
            ),
            pace = "Down in about 2 seconds, up in 1.",
            avoid = "Don't let your hips sag or stick up.",
            easier = "Too hard today? Do them with your knees on the floor.",
        ),
        "press" to Guide(
            oneTime = "Push the dumbbells from your shoulders straight up until your arms are straight, then lower them back to your shoulders. That's 1.",
            steps = listOf(
                "Stand with feet hip-width apart, a dumbbell in each hand at shoulder height, palms facing forward.",
                "Tighten your stomach and squeeze your backside so your back stays straight.",
                "Push straight up until your arms are straight overhead.",
                "Lower slowly back down to your shoulders.",
            ),
            pace = "Up in 1 second, down in 2.",
            avoid = "Don't lean back to help the weight up.",
            easier = "Only one dumbbell? Do the full count with one arm, then the same with the other arm.",
        ),
        "triceps" to Guide(
            oneTime = "With the dumbbell overhead, bend your elbows to lower it behind your head, then straighten your arms again. That's 1.",
            steps = listOf(
                "Stand or sit tall. Hold one dumbbell with both hands, cupping the top end.",
                "Lift it straight up over your head.",
                "Keep your upper arms still next to your ears and bend your elbows to lower the weight behind your head.",
                "Straighten your arms to lift it back up.",
            ),
            pace = "Down in 2 seconds, up in 1.",
            avoid = "Keep your elbows pointing up, not out to the sides. Only go as low as your shoulders feel fine.",
        ),
        "curls" to Guide(
            oneTime = "Bend your elbows to bring the dumbbells up to your shoulders, then lower them until your arms are straight. That's 1.",
            steps = listOf(
                "Stand tall, a dumbbell in each hand, arms hanging at your sides, palms facing forward.",
                "Keep your elbows tucked against your sides the whole time.",
                "Bend your elbows and lift the weights up toward your shoulders.",
                "Lower them slowly all the way down.",
            ),
            pace = "Up in 1 second, down in 2. The slow lowering does a lot of the work.",
            avoid = "Don't swing your body or lean back.",
            easier = "One dumbbell? Do one arm at a time: the full count on one side, then the other.",
        ),
        "row" to Guide(
            oneTime = "Pull the dumbbell up to your hip, then lower it until your arm is straight. That's 1. Do the full count with one arm, then switch.",
            steps = listOf(
                "Put your left knee and left hand on a bench, chair or the edge of a bed. Back flat, like a table.",
                "Hold the dumbbell in your right hand, arm hanging straight down.",
                "Pull it up to your hip with your elbow brushing your side, squeezing your shoulder blade back.",
                "Lower it until your arm is straight. The app tells you when to switch to the other side.",
            ),
            pace = "Up in 1 second, down in 2.",
            avoid = "Don't twist your body to lift it. Only the arm moves.",
        ),
        "ytw" to Guide(
            oneTime = "Lift your arms into a Y and lower, then a T and lower, then a W and lower. All three letters count as 1.",
            steps = listOf(
                "Lie face down on the floor (or bend forward at the hips), forehead resting down.",
                "Y: arms forward in a Y shape, thumbs up. Lift them a few inches off the floor, hold 1 second, lower.",
                "T: arms straight out to the sides. Lift, hold, lower.",
                "W: elbows bent and tucked, hands near your shoulders. Squeeze your shoulder blades together, lift, lower.",
            ),
            pace = "Slow and controlled. A small lift is plenty.",
            avoid = "Don't arch your lower back or swing your arms.",
        ),
        "tristretch" to Guide(
            oneTime = "Hold the stretch until the timer ends, then the app switches you to the other side.",
            steps = listOf(
                "Raise one arm, bend the elbow and let your hand drop down your back.",
                "With the other hand, hold that elbow and gently ease it back.",
                "Breathe slowly and hold. You should feel it along the back of the upper arm.",
            ),
            pace = "Hold still for the timer.",
            avoid = "Ease in. If the elbow itself hurts, back off.",
        ),
        "pecstretch" to Guide(
            oneTime = "Hold the stretch until the timer ends, then the app switches you to the other side.",
            steps = listOf(
                "Stand in a doorway. Put your forearm flat on the frame, elbow at shoulder height.",
                "Step forward gently until you feel a stretch across your chest.",
                "Breathe slowly and hold. No bouncing.",
            ),
            pace = "Hold still for the timer.",
            avoid = "It should feel like a gentle stretch, never pain.",
        ),
        "wallslides" to Guide(
            oneTime = "Slide your arms up the wall until they're nearly straight, then back down. That's 1.",
            steps = listOf(
                "Stand with your back, head and backside against a wall.",
                "Put your arms against the wall in a goalpost shape: elbows bent, hands up.",
                "Slide your arms up, keeping them touching the wall.",
                "Slide them back down to the goalpost.",
            ),
            pace = "About 2 seconds up, 2 seconds down.",
            avoid = "Keep your lower back against the wall.",
        ),
        "circles" to Guide(
            oneTime = "Keep making slow circles until the timer ends.",
            steps = listOf(
                "Hold your arms out to the sides.",
                "Make slow circles with your wrists, then with your forearms from the elbows.",
                "Switch direction halfway through.",
            ),
            pace = "Slow and easy.",
            avoid = "Small, comfortable circles. This is a cool-down.",
        ),
        "hang" to Guide(
            oneTime = "Keep swinging until the timer ends.",
            steps = listOf(
                "Swing both arms open wide, then across your chest like a big hug.",
                "Then roll your shoulders backward in big, slow circles.",
            ),
            pace = "Relaxed, about one swing per second.",
            avoid = "Loose and easy, never forced.",
        ),
    )

    fun guide(id: String): Guide = guides[id] ?: Guide("Do the movement once. That's 1.", emptyList(), "Steady.", "")
}
