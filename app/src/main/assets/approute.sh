#!/system/bin/sh
# Start/stop AppRoute on the phone (runs as the shell user, e.g. via Shizuku or adb).
# Usage: sh /data/local/tmp/approute.sh start|startmap|stop|status|multifocus on|off|state|mickeep on|off|state|mixer|mixstop|players|diag|ask <command>
#   startmap = Custom mode: $D/approute.pkgmap (written by the app, lines "speaker|bt|wired:pkg,pkg") -> uids -> AppRoute map
#   ask      = send one control command to the running router (state | mode | vol up|down <package>) and print its answer
# The app puts its own uid and its control token in the environment (SPLITAUDIO_UID=<uid> SPLITAUDIO_TOKEN=<token> sh approute.sh ...):
#   the router started here inherits both (setsid / nohup keep the environment). It listens on 127.0.0.1:47821 and only serves
#   connections that begin with "auth <token>" and come from that uid, the shell user or root. start / startmap also write the token to
#   $D/approute.token (mode 0600, owned by the shell user, so no app can read it) for "ask". The per-app volume process (mixer) has no
#   control port, so it only inherits the environment.
D=/data/local/tmp; LOG=$D/approute.log
umask 022   # files made here (approute.log, flag files) are 0644, not world-writable; the token file below sets its own 077
# Music apps: audio from these stays on Bluetooth; routing is active only while one of them plays.
#   The app writes the user's choice to $D/approute.music (one package name per line); no file, or nothing valid in it = the common music players (only installed ones count).
DEF_MUSIC="com.google.android.apps.youtube.music app.morphe.android.apps.youtube.music com.spotify.music com.apple.android.music deezer.android.app com.aspiro.tidal com.amazon.mp3 com.soundcloud.android com.pandora.android com.maxmpz.audioplayer"
# the router (start/startmap); the per-app volume process "AppRoute mixer" runs on its own and is not counted here
pid(){ for p in $(pgrep -f "app_process /system/bin AppRoute"); do grep -q mixer /proc/$p/cmdline 2>/dev/null || { echo $p; break; }; done; }
mixpid(){ pgrep -f "[A]ppRoute mixer" | head -1; }   # [A]: never match a shell whose own command line has the words
# Running helpers keep appending to $LOG, so a log over 1 MB is cut in place (same file) to its last 300 lines; called when a helper starts.
trimlog(){ S=$(wc -c < $LOG 2>/dev/null); [ "${S:-0}" -gt 1048576 ] && { tail -n 300 $LOG > $LOG.tmp && cat $LOG.tmp > $LOG; rm -f $LOG.tmp; }; return 0; }
# The control token (32 hex characters from the app). Written to $D/approute.token with mode 0600 and exported for the router that is
# launched next. A router started by hand without a usable one gets a fresh random token, so "approute.sh ask" still works for the shell user.
# Called only right before a router is launched (never when one is already running, so the file always matches the running router).
settoken(){
  T="$SPLITAUDIO_TOKEN"
  case "$T" in *[!0-9a-f]*) T="" ;; esac
  [ "${#T}" = 32 ] || T=$(head -c 16 /dev/urandom 2>/dev/null | od -An -tx1 | tr -d ' \n')
  [ "${#T}" = 32 ] || T=$(tr -d '\n-' < /proc/sys/kernel/random/uuid 2>/dev/null)
  ( umask 077; printf '%s' "$T" > $D/approute.token )
  chmod 600 $D/approute.token
  SPLITAUDIO_TOKEN="$T"; export SPLITAUDIO_TOKEN
}
# "Don't let it pause other apps" (0.5, experimental, Custom mode): $D/approute.nofocus (written by the app, one package name per line)
#   -> appops TAKE_AUDIO_FOCUS ignore for those apps, so they can't take audio focus away from the music app. $D/approute.nofocus.applied
#   remembers what was applied, so an app that was taken off the list (or the whole thing on stop / Simple mode) is set back to default.
nofocus_apply(){
  NEW=$(grep -E '^[A-Za-z0-9_.]+$' $D/approute.nofocus 2>/dev/null | sort -u)
  OLD=$(grep -E '^[A-Za-z0-9_.]+$' $D/approute.nofocus.applied 2>/dev/null | sort -u)
  for p in $OLD; do echo "$NEW" | grep -qx "$p" || { appops set "$p" TAKE_AUDIO_FOCUS default >/dev/null 2>&1; echo "$(date '+%H:%M:%S') nofocus: $p -> default" >> $LOG; }; done
  for p in $NEW; do appops set "$p" TAKE_AUDIO_FOCUS ignore >/dev/null 2>&1; echo "$(date '+%H:%M:%S') nofocus: $p -> ignore" >> $LOG; done
  if [ -n "$NEW" ]; then echo "$NEW" > $D/approute.nofocus.applied; else rm -f $D/approute.nofocus.applied; fi
}
nofocus_revert(){
  for p in $(grep -E '^[A-Za-z0-9_.]+$' $D/approute.nofocus.applied 2>/dev/null); do appops set "$p" TAKE_AUDIO_FOCUS default >/dev/null 2>&1; echo "$(date '+%H:%M:%S') nofocus: $p -> default" >> $LOG; done
  rm -f $D/approute.nofocus.applied
}
case "$1" in
  start)
    trimlog
    [ -n "$(pid)" ] && { echo "already pid=$(pid)"; exit 0; }
    nofocus_revert   # Simple mode has no per-app focus option
    uid(){ pm list packages -U "$1" | grep "package:$1 " | sed 's/.*uid://'; }
    MUSIC=$(grep -E '^[A-Za-z0-9_.]+$' $D/approute.music 2>/dev/null | tr '\n' ' ')   # only valid package names; missing / empty file -> defaults
    [ -z "$(echo $MUSIC)" ] && MUSIC="$DEF_MUSIC"
    M=""; for p in $MUSIC; do u=$(uid "$p"); [ -n "$u" ] && M="$M${M:+,}$u"; done
    # Every app uid except the music apps (including work-profile / second-space uids); system uids (<10000) untouched.
    # User-installed apps (-3) first, so they are always inside the routing limit.
    U=$( { pm list packages -3 -U; pm list packages -U; } | sed 's/.*uid://' | tr ',' '\n' | awk -v m="$M" 'BEGIN{n=split(m,a,",");for(i=1;i<=n;i++)x[a[i]]=1} ($1%100000)>=10000 && !($1 in x) && !($1 in seen){seen[$1]=1;print}' | tr '\n' ' ')
    settoken
    CLASSPATH=$D/approute.dex setsid nohup app_process /system/bin AppRoute "$M" $U >> $LOG 2>&1 < /dev/null &
    sleep 2; echo "started pid=$(pid)"; tail -2 $LOG ;;
  startmap)
    trimlog
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
    nofocus_apply
    settoken
    CLASSPATH=$D/approute.dex setsid nohup app_process /system/bin AppRoute map $D/approute.map >> $LOG 2>&1 < /dev/null &
    sleep 2; echo "started pid=$(pid)"; tail -2 $LOG ;;
  stop) P=$(pid); [ -n "$P" ] && kill $P; nofocus_revert; echo "stopped" ;;
  status) P=$(pid); echo "pid=${P:-none}"; tail -3 $LOG ;;
  # Per-app volume (0.4): $D/approute.volpkg (written by the app, lines "pkg=0.35", 0 = mute) -> uids -> AppRoute mixer.
  #   Already running = it re-reads the file by itself. No app below 100 % = mixer off (it resets the volumes first).
  mixer)
    trimlog
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
  # 0.5.7: what the one-tap report attaches about audio focus (read-only): the multi-audio-focus setting, the mic switch, the router,
  #   "AppRoute diag" (audio mode, focus stack, who is playing) and the last lines of the router's own log.
  diag)
    echo "multi_audio_focus_enabled=$(settings get system multi_audio_focus_enabled 2>/dev/null)"
    [ -e $D/approute.nomickeep ] && echo "mickeep=off" || echo "mickeep=on"
    P=$(pid); echo "router pid=${P:-none}"
    CLASSPATH=$D/approute.dex app_process /system/bin AppRoute diag 2>&1 | tail -n 20
    echo "router log:"; tail -n 20 $LOG 2>/dev/null ;;
  # talk to the running router as the shell user (debugging, scripts): sh approute.sh ask state (AppRoute reads $D/approute.token itself)
  ask) shift; CLASSPATH=$D/approute.dex app_process /system/bin AppRoute ask "$*" ;;
  # "A video doesn't pause the music" (Android multi audio focus); see AppRoute.java.
  #   on = the user agreed (writes approute.mf_ok, removes approute.nomf), off = declined (the other way round); neither file = not decided yet
  multifocus) CLASSPATH=$D/approute.dex app_process /system/bin AppRoute multifocus "${2:-state}" 2>&1 | tee -a $LOG | tail -1 ;;
  # "Pressing the mic doesn't pause the music" (voice typing / an assistant; see focusWait in AppRoute.java). On is the default = no flag file.
  #   off = $D/approute.nomickeep exists. The router looks at that file each time it needs it, so no restart. Prints the state: mickeep=on|off
  mickeep)
    case "$2" in
      on) rm -f $D/approute.nomickeep ;;
      off) : > $D/approute.nomickeep; chmod 644 $D/approute.nomickeep ;;
    esac
    [ -e $D/approute.nomickeep ] && echo "mickeep=off" || echo "mickeep=on" ;;
esac
