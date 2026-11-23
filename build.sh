#!/bin/sh
# Builds Shaxmat.jar / Shaxmat.jad for Java ME (CLDC 1.1 / MIDP 2.0). Needs only a JDK and python3.
set -e
cd "$(dirname "$0")"
VERSION=1.1.0
B=build
rm -rf $B/cldc $B/midp $B/classes $B/me $B/check $B/tools $B/dist
mkdir -p $B/cldc $B/midp $B/classes $B/me $B/check $B/tools $B/dist
filt() { grep -v -e JAVA_TOOL -e 'warning:' -e '^[0-9]* warning' -e 'Note:' || true; }

# 1. CLDC 1.1 API (signatures only) - the app may only use what is declared here
javac -nowarn -source 8 -target 8 -bootclasspath $B/cldc -d $B/cldc $(find stubs/cldc -name '*.java') 2>&1 | filt
test -f $B/cldc/java/lang/Object.class
# 2. MIDP 2.0 API, implemented as a small desktop emulator (compile target + desktop tests)
javac -nowarn -source 8 -target 8 -d $B/midp $(find stubs/midp -name '*.java') 2>&1 | filt
test -f $B/midp/javax/microedition/lcdui/Canvas.class
# 3. the MIDlet, compiled against CLDC + MIDP only
javac -nowarn -g:none -source 8 -target 8 -bootclasspath $B/cldc:$B/midp -d $B/classes $(find src -name '*.java') 2>&1 | filt
test -f $B/classes/uz/shaxmat/ShaxmatMIDlet.class
# 4. preverify for Java ME (StackMap, class version 47) + a desktop-verifiable copy
javac -nowarn -d $B/tools tools/Preverify.java 2>&1 | filt
java -cp $B/tools Preverify $B/classes $B/me 2>&1 | filt
java -cp $B/tools Preverify --check $B/classes $B/check 2>&1 | filt
python3 tools/refcheck.py $B/me $B/cldc $B/midp > $B/refcheck.txt || { cat $B/refcheck.txt; exit 1; }
tail -1 $B/refcheck.txt
# 5. jar + jad
cat > $B/MANIFEST.MF <<EOM
Manifest-Version: 1.0
MIDlet-Name: Shaxmat
MIDlet-Vendor: Shaxmat
MIDlet-Version: $VERSION
MIDlet-Icon: /icon.png
MIDlet-1: Shaxmat, /icon.png, uz.shaxmat.ShaxmatMIDlet
MIDlet-Description: Kuchli shaxmat boti
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1
Nokia-MIDlet-Category: Game
EOM
cp res/*.png $B/me/
cp res/*.png $B/check/
# plain zip entries (sizes in local headers, no data descriptors) - safest for old phones
python3 - "$B" <<'EOP'
import os, sys, zipfile
b = sys.argv[1]
with zipfile.ZipFile(os.path.join(b, 'dist', 'Shaxmat.jar'), 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/MANIFEST.MF', open(os.path.join(b, 'MANIFEST.MF')).read().replace('\n', '\r\n'))
    for root, _, files in sorted(os.walk(os.path.join(b, 'me'))):
        for f in sorted(files):
            full = os.path.join(root, f)
            z.write(full, os.path.relpath(full, os.path.join(b, 'me')).replace(os.sep, '/'))
EOP
SIZE=$(wc -c < $B/dist/Shaxmat.jar | tr -d ' ')
{ grep -v Manifest-Version $B/MANIFEST.MF; echo "MIDlet-Jar-URL: Shaxmat.jar"; echo "MIDlet-Jar-Size: $SIZE"; } > $B/dist/Shaxmat.jad
echo "Built $B/dist/Shaxmat.jar ($SIZE bytes)"
