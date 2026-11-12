package com.hurricanes92xx.freedomiptv.sports.data
import com.hurricanes92xx.freedomiptv.sports.model.Competition
import com.hurricanes92xx.freedomiptv.sports.model.Game
import com.hurricanes92xx.freedomiptv.sports.model.Team
/** Provider-neutral contracts so feeds can be swapped without rewriting UI. */
interface SportsProvider { val id:String; suspend fun teams(competition:Competition):List<Team>; suspend fun schedule(competition:Competition,fromUtcMillis:Long,toUtcMillis:Long):List<Game>; suspend fun liveScores(competition:Competition):List<Game> }
