#!/system/bin/sh
# Start/stop AppRoute on the phone (runs as the shell user, e.g. via Shizuku or adb).
# Usage: sh /data/local/tmp/approute.sh start|startmap|stop|status|multifocus on|off|state|mixer|mixstop|players
#   startmap = Custom mode: $D/approute.pkgmap (written by the app, lines "speaker|bt|wired:pkg,pkg") -> uids -> AppRoute map
D=/data/local/tmp; LOG=$D/approute.log
# Music apps: audio from these stays on Bluetooth; routing is active only while one of them plays.
MUSIC="app.morphe.android.apps.youtube.music com.google.android.apps.youtube.music"
# the router (start/startmap); the per-app volume process "AppRoute mixer" runs on its own and is not counted here
pid(){ for p in $(pgrep -f "app_process /system/bin AppRoute"); do grep -q mixer /proc/$p/cmdline 2>/dev/null || { echo $p; break; }; done; }
mixpid(){ pgrep -f "[A]ppRoute mixer" | head -1; }   # [A]: never match a shell whose own command line has the words
case "$1" in
  start)
    [ -n "$(pid)" ] && { echo "already pid=$(pid)"; exit 0; }
    uid(){ pm list packages -U "$1" | grep "package:$1 " | sed 's/.*uid://'; }
    M=""; for p in $MUSIC; do u=$(uid "$p"); [ -n "$u" ] && M="$M${M:+,}$u"; done
    # Every app uid except the music apps (including work-profile / second-space uids); system uids (<10000) untouched.
    # User-installed apps (-3) first, so they are always inside the routing limit.
    U=$( { pm list packages -3 -U; pm list packages -U; } | sed 's/.*uid://' | tr ',' '\n' | awk -v m="$M" 'BEGIN{n=split(m,a,",");for(i=1;i<=n;i++)x[a[i]]=1} ($1%100000)>=10000 && !($1 in x) && !($1 in seen){seen[$1]=1;print}' | tr '\n' ' ')
    CLASSPATH=$D/approute.dex setsid nohup app_process /system/bin AppRoute "$M" $U >> $LOG 2>&1 < /dev/null &
    sleep 2; echo "started pid=$(pid)"; tail -2 $LOG ;;
  startmap)
    [ -n "$(pid)" ] && { echo "already pid=$(pid)"; exit 0; }
    # package names -> uids (all users / second space too, like start); one pm call, unknown packages skipped
    ALL=$(pm list packages -U)
    : > $D/approute.map.new
    while IFS=: read t pk; do
      [ -z "$t" ] && continue
      L=""; for p in $(echo "$pk" | tr ',' ' '); do u=$(echo "$ALL" | grep "^package:$p " | sed 's/.*uid://'); [ -n "$u" ] && L="$L${L:+,}$u"; done
      [ -n "$L" ] && echo "$t:$L" >> $D/approute.map.new
    done < $D/approute.pkgmap
    chmod 644 $D/approute.map.new; mv -f $D/approute.map.new $D/approute.map
    [ -s $D/approute.map ] || { echo "empty map"; exit 1; }
    CLASSPATH=$D/approute.dex setsid nohup app_process /system/bin AppRoute map $D/approute.map >> $LOG 2>&1 < /dev/null &
    sleep 2; echo "started pid=$(pid)"; tail -2 $LOG ;;
  stop) P=$(pid); [ -n "$P" ] && kill $P; echo "stopped" ;;
  status) P=$(pid); echo "pid=${P:-none}"; tail -3 $LOG ;;
  # Per-app volume (0.4): $D/approute.volpkg (written by the app, lines "pkg=0.35", 0 = mute) -> uids -> AppRoute mixer.
  #   Already running = it re-reads the file by itself. No app below 100 % = mixer off (it resets the volumes first).
  mixer)
    ALL=$(pm list packages -U)
    : > $D/approute.vol.new
    while IFS== read pk v; do
      [ -z "$pk" ] && continue
      for u in $(echo "$ALL" | grep "^package:$pk " | sed 's/.*uid://' | tr ',' ' '); do echo "$u=$v" >> $D/approute.vol.new; done
    done < $D/approute.volpkg
    chmod 644 $D/approute.vol.new; mv -f $D/approute.vol.new $D/approute.vol
    [ -s $D/approute.vol ] || { rm -f $D/approute.vol; echo "mixer off"; exit 0; }
    [ -n "$(mixpid)" ] && { echo "mixer updated pid=$(mixpid)"; exit 0; }
    CLASSPATH=$D/approute.dex setsid nohup app_process /system/bin AppRoute mixer $D/approute.vol >> $LOG 2>&1 < /dev/null &
    sleep 1; echo "mixer started pid=$(mixpid)" ;;
  mixstop) rm -f $D/approute.vol; sleep 2; P=$(mixpid); [ -n "$P" ] && kill $P; echo "mixer stopped" ;;
  players) CLASSPATH=$D/approute.dex app_process /system/bin AppRoute players ;;
  # "A video doesn't pause the music" (Android multi audio focus); see AppRoute.java
  multifocus) CLASSPATH=$D/approute.dex app_process /system/bin AppRoute multifocus "${2:-state}" 2>&1 | tee -a $LOG | tail -1 ;;
esac
