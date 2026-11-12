package com.hurricanes92xx.freedomiptv.sports.matching

import com.hurricanes92xx.freedomiptv.provider.xtream.XtreamLiveChannel
import com.hurricanes92xx.freedomiptv.sports.model.ChannelCandidate

/** Explainable lineup matcher; generic sports labels never count as verified game availability. */
object ChannelMatcher {
 private val aliases=mapOf("espn" to listOf("espn","espn hd","espn fhd","espn us"),"espn2" to listOf("espn 2","espn2"),"fs1" to listOf("fs1","fox sports 1"),"fs2" to listOf("fs2","fox sports 2"),"btn" to listOf("btn","big ten network"),"sec" to listOf("sec network","sec"),"acc" to listOf("acc network","accn"),"tnt" to listOf("tnt","tnt sports"))
 fun normalize(value:String)=value.lowercase().replace(Regex("\\b(4k|uhd|fhd|hd|sd|us|east|west)\\b")," ").replace(Regex("[^a-z0-9]+")," ").trim().replace(Regex("\\s+")," ")
 fun match(channels:List<XtreamLiveChannel>,network:String,eventTerms:List<String> = emptyList()):List<ChannelCandidate>{
  val aliases=(aliases[normalize(network)]?:listOf(network)).map(::normalize).toSet()
  return channels.mapNotNull { c -> val n=normalize(c.name);var score=0;val reasons=mutableListOf<String>()
   if(n in aliases){score+=90;reasons.add("network exact/alias")} else if(aliases.any{n.startsWith("$it ")||n.endsWith(" $it")}){score+=72;reasons.add("network alias")}
   val hits=eventTerms.map(::normalize).filter{it.length>=3&&n.contains(it)}.distinct();if(hits.isNotEmpty()){score+=minOf(30,hits.size*12);reasons.add("event terms: "+hits.joinToString())}
   if(Regex("\\b(sports|sport|event)\\b").containsMatchIn(n)){score+=5;reasons.add("generic sports label")}
   if(score==0)null else ChannelCandidate(c.id,c.name,score.coerceAtMost(100),reasons)
  }.sortedByDescending{it.confidence}.take(8)
 }
}
