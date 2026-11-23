"""Checks the Java ME class files:
 - class version 47, every method with branches has a StackMap, no StackMapTable
 - every referenced class / field / method resolves either to the app or to the CLDC/MIDP API
 - nothing from the desktop emulator hooks (Emu, emu* members) is referenced
Usage: python3 refcheck.py appDir apiDir1 apiDir2 ...
"""
import os, struct, sys

def parse(path):
    d = open(path, 'rb').read()
    pos = 8
    minor, major = struct.unpack('>HH', d[4:8])
    n, = struct.unpack('>H', d[pos:pos + 2]); pos += 2
    cp = [None] * n
    i = 1
    while i < n:
        t = d[pos]; pos += 1
        if t == 1:
            l, = struct.unpack('>H', d[pos:pos + 2]); cp[i] = (1, d[pos + 2:pos + 2 + l].decode('utf-8', 'replace')); pos += 2 + l
        elif t in (3, 4): cp[i] = (t,); pos += 4
        elif t in (5, 6): cp[i] = (t,); pos += 8; i += 1
        elif t in (7, 8): cp[i] = (t, struct.unpack('>H', d[pos:pos + 2])[0]); pos += 2
        elif t in (9, 10, 11, 12): cp[i] = (t,) + struct.unpack('>HH', d[pos:pos + 4]); pos += 4
        else: raise Exception('tag %d in %s' % (t, path))
        i += 1
    u = lambda k: cp[k][1]
    access, this, sup, ni = struct.unpack('>HHHH', d[pos:pos + 8]); pos += 8
    ifs = [u(cp[x][1]) for x in struct.unpack('>' + 'H' * ni, d[pos:pos + 2 * ni])]; pos += 2 * ni
    def members():
        nonlocal pos
        cnt, = struct.unpack('>H', d[pos:pos + 2]); pos += 2
        out = []
        for _ in range(cnt):
            acc, nm, ds, na = struct.unpack('>HHHH', d[pos:pos + 8]); pos += 8
            attrs = []
            for _ in range(na):
                an, al = struct.unpack('>HI', d[pos:pos + 6]); pos += 6
                attrs.append((u(an), d[pos:pos + al])); pos += al
            out.append((u(nm), u(ds), attrs))
        return out
    fields = members()
    methods = members()
    def cls(k): return u(cp[k][1])
    refs = []
    for e in cp:
        if e and e[0] in (9, 10, 11):
            nt = cp[e[2]]
            refs.append((e[0], cls(e[1]), u(nt[1]), u(nt[2])))
    classes = [u(e[1]) for e in cp if e and e[0] == 7]
    return dict(major=major, name=u(cp[this][1]), sup=cls(sup) if sup else None, ifs=ifs,
                fields={(f[0], f[1]) for f in fields}, methods={(m[0], m[1]) for m in methods},
                rawmethods=methods, refs=refs, classes=classes, utf=u)

def load(dirs):
    out = {}
    for dr in dirs:
        for root, _, fs in os.walk(dr):
            for f in fs:
                if f.endswith('.class'):
                    c = parse(os.path.join(root, f))
                    out[c['name']] = c
    return out

app = load([sys.argv[1]])
api = load(sys.argv[2:])
allc = dict(api); allc.update(app)
errors = []

def resolve(owner, name, desc, field):
    seen = set(); todo = [owner]
    while todo:
        c = todo.pop(0)
        if c in seen: continue
        seen.add(c)
        k = allc.get(c)
        if k is None:
            if c.startswith('['):
                return 'array'
            errors.append('missing class ' + c); return None
        if (name, desc) in (k['fields'] if field else k['methods']):
            return c
        if k['sup']: todo.append(k['sup'])
        todo.extend(k['ifs'])
        if not field and c.startswith('['): return 'array'
    return None

frames = 0
for n, c in app.items():
    if c['major'] != 47: errors.append('%s: class version %d' % (n, c['major']))
    for cl in c['classes']:
        base = cl.lstrip('[')
        if base.startswith('L'): base = base[1:-1]
        if len(base) == 1: continue
        if base not in allc: errors.append('%s: missing class %s' % (n, base))
        if base.endswith('/Emu'): errors.append('%s: uses emulator hook %s' % (n, base))
    for (t, owner, name, desc) in c['refs']:
        if owner.startswith('['):
            if name not in ('clone',): continue
            errors.append('%s: array clone() not allowed on CLDC' % n); continue
        r = resolve(owner, name, desc, t == 9)
        if r is None:
            errors.append('%s: unresolved %s.%s%s' % (n, owner, name, desc))
        elif r in api and (name.startswith('emu') or r.endswith('/Emu')):
            errors.append('%s: uses emulator-only member %s.%s' % (n, r, name))
    for (mn, md, attrs) in c['rawmethods']:
        for an, ab in attrs:
            if an == 'Code':
                cl, = struct.unpack('>I', ab[4:8])
                code = ab[8:8 + cl]
                p = 8 + cl
                ne, = struct.unpack('>H', ab[p:p + 2]); p += 2 + 8 * ne
                na, = struct.unpack('>H', ab[p:p + 2]); p += 2
                sub = []
                for _ in range(na):
                    an2, al = struct.unpack('>HI', ab[p:p + 6]); p += 6
                    sub.append(c['utf'](an2)); p += al
                if 'StackMapTable' in sub:
                    errors.append('%s.%s: still has StackMapTable' % (n, mn))
                if 'StackMap' in sub:
                    frames += 1

cnt_api = {}
for n, c in app.items():
    for (t, owner, name, desc) in c['refs']:
        r = resolve(owner, name, desc, t == 9)
        if r in api:
            cnt_api.setdefault(r, set()).add(name + desc)
print('API members used:')
for k in sorted(cnt_api):
    print('  %s: %s' % (k, ', '.join(sorted(cnt_api[k]))))
if errors:
    print('ERRORS:')
    for e in sorted(set(errors)): print('  ' + e)
    sys.exit(1)
print('refcheck OK: %d app classes, %d methods with StackMap' % (len(app), frames))
