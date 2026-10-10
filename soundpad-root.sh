#!/data/data/com.termux/files/usr/bin/bash
# Мост Soundpad: root-часть работает через Termux (у него root есть).
# Приложение шлёт звук на 127.0.0.1:7788, скрипт подаёт его в микрофонную петлю.
PORT=7788
command -v nc >/dev/null || pkg install -y netcat-openbsd
command -v aplay >/dev/null || pkg install -y alsa-utils
termux-wake-lock 2>/dev/null

cat > "$HOME/.sp-root.sh" << EOS
export LD_LIBRARY_PATH=$PREFIX/lib
B=$PREFIX/bin
\$B/amixer -c 0 cset name='MultiMedia1 Mixer AFE_PCM_TX' 1 >/dev/null 2>&1
\$B/amixer -c 0 cset name='Voip_Tx Mixer AFE_PCM_TX_Voip' 1 >/dev/null 2>&1
\$B/aplay -q -D hw:0,5 -t raw -f S16_LE -r 48000 -c 2 --buffer-time=100000
\$B/amixer -c 0 cset name='MultiMedia1 Mixer AFE_PCM_TX' 0 >/dev/null 2>&1
\$B/amixer -c 0 cset name='Voip_Tx Mixer AFE_PCM_TX_Voip' 0 >/dev/null 2>&1
EOS

echo "Мост Soundpad слушает 127.0.0.1:$PORT. Не закрывай Termux. Остановка: Ctrl+C"
while true; do
  nc -l 127.0.0.1 "$PORT" | su -c "sh $HOME/.sp-root.sh"
  sleep 0.3
done
