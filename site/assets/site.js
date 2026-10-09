(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const fine = matchMedia('(hover: hover) and (pointer: fine)').matches;
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  $('#yr').textContent = new Date().getFullYear();

  // hide a frame whose screenshot is missing rather than show a broken image
  $$('img[src^="img/shots/"]').forEach(i => i.addEventListener('error', () => { const f = i.closest('.device,.device-sm'); (f || i).style.visibility = 'hidden'; }));

  // ---------------- cast gallery
  const CAST = [['zara', 'Zara', 'Snow leopard'], ['taj', 'Taj', 'Markhor'], ['kami', 'Kami', 'Camel'], ['shaheen', 'Shaheen', 'Falcon'], ['motu', 'Motu', 'Panda'],
    ['chakor', 'Chakor', 'Partridge'], ['monal', 'Monal', 'Pheasant'], ['mor', 'Mor', 'Peacock'], ['bhalu', 'Bhalu', 'Brown bear'], ['lomri', 'Lomri', 'Fox'],
    ['khargosh', 'Khargosh', 'Hare'], ['ullu', 'Ullu', 'Owl'], ['yaku', 'Yaku', 'Yak'], ['sehi', 'Sehi', 'Porcupine']];
  const grid = $('#castGrid');
  CAST.forEach(([id, n, k]) => {
    const f = document.createElement('figure');
    f.className = 'tilt reveal';
    f.innerHTML = `<img src="img/cast/${id}.webp" alt="${n} the ${k.toLowerCase()}" loading="lazy"><figcaption>${n}<small>${k}</small></figcaption>`;
    grid.appendChild(f);
  });

  // ---------------- food grid
  const FOODS = [['nihari', 'Nihari'], ['omelette', 'Omelette'], ['chicken_biryani', 'Biryani'], ['salad', 'Salad'], ['chicken_karahi', 'Karahi'], ['oats', 'Oats'], ['chapli_kebab', 'Chapli kebab'], ['mango_lassi', 'Mango lassi'],
    ['beef_burger', 'Burger'], ['halwa_puri', 'Halwa puri'], ['banana', 'Banana'], ['gol_gappay', 'Gol gappay'], ['club_sandwich', 'Club sandwich'], ['seekh_kebab', 'Seekh kebab'], ['apple', 'Apple'], ['jalebi', 'Jalebi'],
    ['daal_chawal', 'Daal chawal'], ['chocolate_shake', 'Chocolate shake'], ['haleem', 'Haleem'], ['paratha_plain', 'Paratha'], ['kebab_roll', 'Kebab roll'], ['tea_sugar', 'Chai'], ['kheer', 'Kheer'], ['lassi_sweet', 'Lassi']];
  const fo = $('#foodOrbit');
  FOODS.forEach(([id, n], i) => {
    const f = document.createElement('figure');
    f.className = 'reveal';
    f.style.transitionDelay = (i % 8) * 60 + 'ms';
    f.innerHTML = `<img src="img/food/${id}.webp" alt="${n}" loading="lazy"><figcaption>${n}</figcaption>`;
    fo.appendChild(f);
  });

  // ---------------- headline word split
  $$('.split').forEach(h => {
    const walk = n => [...n.childNodes].forEach(c => {
      if (c.nodeType === 3) {
        const frag = document.createDocumentFragment();
        c.textContent.split(/(\s+)/).forEach(w => { if (!w) return; if (/^\s+$/.test(w)) frag.append(w); else { const s = document.createElement('span'); s.className = 'w'; s.textContent = w; frag.append(s); } });
        c.replaceWith(frag);
      } else if (c.nodeType === 1) walk(c);
    });
    walk(h);
    $$('.w', h).forEach((w, i) => { w.style.transitionDelay = 120 + i * 70 + 'ms'; });
  });

  // ---------------- reveal on scroll
  const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }), { threshold: .12, rootMargin: '0px 0px -40px 0px' });
  $$('.reveal').forEach((el, i) => { if (!el.style.transitionDelay) el.style.transitionDelay = (el.closest('.pip-row,.cast-grid,.bento,.faq-list,.coach-grid,.cam-feats,.coach-shots') ? (i % 7) * 70 : 0) + 'ms'; io.observe(el); });

  // ---------------- nav + progress bar
  const nav = $('#nav'), bar = $('.progress-bar');
  const onScroll = () => {
    nav.classList.toggle('solid', scrollY > 40);
    bar.style.transform = `scaleX(${scrollY / Math.max(1, document.documentElement.scrollHeight - innerHeight)})`;
  };
  addEventListener('scroll', onScroll, { passive: true }); onScroll();

  // ---------------- cursor glow, cursor dot, mouse + scroll parallax
  const glow = $('.cursor-glow'), dot = $('.cursor-dot');
  let mx = innerWidth / 2, my = innerHeight / 2, tx = mx, ty = my;
  addEventListener('pointermove', e => { mx = e.clientX; my = e.clientY; if (dot) dot.style.transform = `translate(${mx}px, ${my}px)`; }, { passive: true });
  $$('a, button, .tilt, .food-orbit figure').forEach(el => {
    el.addEventListener('pointerenter', () => dot && dot.classList.add('big'));
    el.addEventListener('pointerleave', () => dot && dot.classList.remove('big'));
  });
  const layers = $$('#heroArt [data-depth]');
  const speedEls = $$('[data-speed]');
  const rows = $$('.wall-row');
  const wall = $('.wall');
  function frame() {
    tx += (mx - tx) * .1; ty += (my - ty) * .1;
    if (glow) glow.style.transform = `translate(${tx - 260}px, ${ty - 260}px)`;
    const nx = (tx / innerWidth - .5), ny = (ty / innerHeight - .5);
    const sy = scrollY;
    layers.forEach(l => { const d = +l.dataset.depth; l.style.translate = `${-nx * 46 * d}px ${-ny * 34 * d - sy * .14 * d}px`; });
    speedEls.forEach(l => {
      const r = l.getBoundingClientRect(); const c = (r.top + r.height / 2 - innerHeight / 2);
      l.style.transform = l.classList.contains('blob') ? `translateY(${sy * +l.dataset.speed}px)` : `translateY(${c * +l.dataset.speed}px)`;
    });
    if (wall) {
      const r = wall.getBoundingClientRect();
      if (r.bottom > 0 && r.top < innerHeight) {
        const p = (innerHeight - r.top) / (innerHeight + r.height);
        rows.forEach(row => { const dir = +row.dataset.dir; const span = row.scrollWidth - innerWidth; row.style.transform = `translateX(${dir < 0 ? -p * span * .6 : -span * .6 + p * span * .6}px)`; });
      }
    }
    requestAnimationFrame(frame);
  }
  if (!reduce) requestAnimationFrame(frame);

  // ---------------- magnetic buttons
  if (fine && !reduce) $$('.magnetic').forEach(b => {
    b.addEventListener('pointermove', e => { const r = b.getBoundingClientRect(); b.style.transform = `translate(${(e.clientX - r.left - r.width / 2) * .25}px, ${(e.clientY - r.top - r.height / 2) * .35}px)`; });
    b.addEventListener('pointerleave', () => { b.style.transform = ''; });
  });

  // ---------------- 3D tilt + glare
  const tilt = (c, e, k) => {
    const r = c.getBoundingClientRect(); const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
    c.style.transform = `perspective(900px) rotateY(${x * k}deg) rotateX(${-y * k}deg)`;
  };
  if (fine && !reduce) {
    $$('.tilt').forEach(c => { c.addEventListener('pointermove', e => tilt(c, e, 10)); c.addEventListener('pointerleave', () => { c.style.transform = ''; }); });
    grid.addEventListener('pointermove', e => { const c = e.target.closest('.tilt'); if (c) tilt(c, e, 14); });
    grid.addEventListener('pointerout', e => { const c = e.target.closest('.tilt'); if (c) c.style.transform = ''; });
  }

  // ---------------- sticky showcase: swap the phone screen as each step scrolls in
  const steps = $$('.sc-step'), shots = $$('#scScreens img'), dots = $('#scDots');
  steps.forEach((s, i) => {
    s.style.setProperty('--shot', `url(../img/shots/${s.dataset.shot}.webp)`);
    const d = document.createElement('i'); d.addEventListener('click', () => s.scrollIntoView({ behavior: 'smooth', block: 'center' })); dots.appendChild(d);
  });
  const setStep = k => {
    steps.forEach((s, i) => { const on = s.dataset.shot === k; s.classList.toggle('on', on); dots.children[i].classList.toggle('on', on); });
    shots.forEach(im => im.classList.toggle('on', im.dataset.k === k));
  };
  const sio = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) setStep(e.target.dataset.shot); }), { rootMargin: '-45% 0px -45% 0px' });
  steps.forEach(s => sio.observe(s));
  setStep('home');

  // ---------------- camera coach demo: reps, angle, form score, cue
  const cues = ['“Great depth — chest up!”', '“Knees out, nice!”', '“Slow on the way down.”', '“Two more — push!”', '“Hips level — perfect.”', '“Last one, make it count!”'];
  const cRep = $('#camRep'), cDeg = $('#camDeg'), cSc = $('#camScore'), cRing = $('#camRing'), cCue = $('#camCue'), legs = $$('.cam-frame .leg');
  const exs = $$('.ex-cloud span');
  if (cRep && !reduce) {
    let n = 8, down = false, ci = 0, ei = 0;
    setInterval(() => {
      down = !down;
      cDeg.textContent = (down ? 88 + Math.round(Math.random() * 8) : 168) + '°';
      legs.forEach(l => l.setAttribute('d', down ? (l === legs[0] ? 'M100 120 L72 160 L95 230' : 'M100 120 L133 160 L110 230') : (l === legs[0] ? 'M100 120 L80 175 L95 230' : 'M100 120 L125 175 L110 230')));
      if (!down) {
        n = n >= 12 ? 1 : n + 1; cRep.textContent = n;
        const sc = 82 + Math.round(Math.random() * 17); cSc.textContent = sc;
        cRing.style.strokeDashoffset = 119.4 * (1 - sc / 100);
        cRing.style.stroke = sc >= 90 ? '#7cf2c4' : '#ffcf5a';
        cCue.classList.add('swap');
        setTimeout(() => { cCue.textContent = cues[ci++ % cues.length]; cCue.classList.remove('swap'); }, 300);
        exs.forEach(x => x.classList.remove('lit')); exs[ei++ % exs.length].classList.add('lit');
      }
    }, 1100);
  }

  // ---------------- coach look switchers + pose cycling on hover
  const coachSwitch = (card, imgSel, poses) => {
    const img = $(imgSel); let look = $('.looks button.on', card).dataset.look, pi = 0, timer;
    const show = src => { img.classList.add('swap'); setTimeout(() => { img.src = src; img.onload = () => img.classList.remove('swap'); }, 200); };
    $$('.looks button', card).forEach(b => b.addEventListener('click', e => {
      e.stopPropagation(); $$('.looks button', card).forEach(x => x.classList.remove('on')); b.classList.add('on');
      look = b.dataset.look; pi = 0; show(`img/coach/${look}_${poses[0]}.webp`);
    }));
    card.addEventListener('pointerenter', () => { if (reduce) return; clearInterval(timer); timer = setInterval(() => { pi = (pi + 1) % poses.length; show(`img/coach/${look}_${poses[pi]}.webp`); }, 1500); });
    card.addEventListener('pointerleave', () => { clearInterval(timer); });
  };
  coachSwitch($('[data-coach="bolt"]'), '#boltImg', ['stand', 'demo', 'cheer', 'point', 'clipboard']);
  coachSwitch($('[data-coach="zest"]'), '#zestImg', ['plate', 'thumbs', 'think', 'cart', 'stand']);

  // ---------------- live notification demo
  const lf = $('#liveFill'), lb = $('#liveBadge'), lt = $('#liveTxt'), clk = $('#lockClock');
  if (lf) {
    let rest = 45;
    const tick = () => {
      rest = rest <= 0 ? 45 : rest - 1;
      const p = 25 + (1 - rest / 45) * 25; lf.style.width = p + '%'; lb.style.left = p + '%';
      lt.textContent = rest > 0 ? `Rest 0:${String(rest).padStart(2, '0')}` : 'Go! Set 4 💪';
    };
    if (!reduce) setInterval(tick, 1000);
    clk.textContent = (d.getHours() % 12 || 12) + ':' + String(d.getMinutes()).padStart(2, '0');
    clk.nextElementSibling.textContent = d.toLocaleDateString('en', { weekday: 'long' });
  }

  // ---------------- theme demo (colours echo the app's families)
  const THEMES = [['Kinetic', 'Essentials', '#7cf2c4', '#173c33', '#07120f'], ['Cobalt', 'Essentials', '#5ab8ff', '#13284a', '#070c16'], ['Blaze', 'Essentials', '#ff8a4c', '#4a1e10', '#130906'],
    ['True Black', 'Dark & AMOLED', '#e8e8e8', '#202020', '#000000'], ['Cosmic', 'Dark & AMOLED', '#b48cff', '#2a1850', '#0a0614'], ['Web Crimson', 'Hero moods', '#ff4a5a', '#3a0d18', '#10050a'],
    ['Reactor Gold', 'Hero moods', '#ffc94a', '#4a2a08', '#120b04'], ['Racing Red', 'Cars & bikes', '#ff3b30', '#2a2a2a', '#0b0b0b'], ['Carbon Lime', 'Cars & bikes', '#c6ff3d', '#22301a', '#0a0d08'],
    ['Hunza', 'Nature', '#ffb36b', '#1d3b4a', '#071016'], ['Sakura', 'Nature', '#ff9ec7', '#3d1d33', '#120810'], ['Truck Art', 'Pakistan', '#ffd23f', '#c2185b', '#120714']];
  const sw = $('#swatches'), tp = $('#tdPreview'), tn = $('#tdName');
  THEMES.forEach(([n, fam, a, b, bg], i) => {
    const btn = document.createElement('button'); btn.style.setProperty('--a', a); btn.style.setProperty('--b', b); btn.setAttribute('aria-label', n);
    btn.addEventListener('click', () => { $$('button', sw).forEach(x => x.classList.remove('on')); btn.classList.add('on'); tp.style.setProperty('--a', a); tp.style.setProperty('--b', b); tp.style.setProperty('--bg', bg); tn.textContent = `${n} · ${fam}`; });
    if (!i) btn.classList.add('on');
    sw.appendChild(btn);
  });
  $$('.seg button').forEach(b => b.addEventListener('click', () => { $$('.seg button').forEach(x => x.classList.remove('on')); b.classList.add('on'); tp.classList.toggle('flat', b.dataset.style === 'flat'); tp.classList.toggle('glass', b.dataset.style !== 'flat'); }));
  // auto-cycle until the visitor touches it
  let auto = !reduce && setInterval(() => { const bs = $$('button', sw); const k = (bs.findIndex(x => x.classList.contains('on')) + 1) % bs.length; bs[k].click(); }, 2600);
  $('.theme-demo').addEventListener('pointerdown', () => { clearInterval(auto); auto = 0; });

  // ---------------- release info + QR
  try { new QRCode($('#qr'), { text: location.origin + location.pathname, width: 150, height: 150, correctLevel: QRCode.CorrectLevel.M }); } catch (e) {}
  fetch('https://api.github.com/repos/Sohaib020/myfit-tracker/releases/latest').then(r => r.json()).then(j => {
    const build = (j.tag_name || '').replace('build-', '');
    const a = (j.assets || []).find(x => x.name === 'MyFitTracker.apk') || (j.assets || []).find(x => x.name.endsWith('.apk'));
    const mb = a ? ' · ' + Math.round(a.size / 1048576) + ' MB' : '';
    const d = (j.published_at || '').slice(0, 10);
    if (build) { const t = 'Latest: <b>build ' + build + '</b>' + mb + (d ? ' · ' + d : '') + ' · Android 8.0+'; $('#ver').innerHTML = t; $('#ver2').innerHTML = t; }
    const notes = (j.body || '').split('\n').filter(l => l.trim() && !/^(Co-Authored-By|Claude-Session|🤖)/.test(l)).slice(0, 6).join('\n');
    if (notes) $('#notes').textContent = "What's new\n" + notes;
  }).catch(() => {});

  // ---------------- GSAP extras + count-ups
  addEventListener('load', () => {
    if (window.gsap && window.ScrollTrigger && !reduce) {
      gsap.registerPlugin(ScrollTrigger);
      gsap.to('.hero-copy', { y: -90, opacity: .15, ease: 'none', scrollTrigger: { trigger: '.hero', start: 'top top', end: 'bottom top', scrub: true } });
      gsap.fromTo('.sc-phone', { rotate: -4, scale: .92 }, { rotate: 3, scale: 1, ease: 'none', scrollTrigger: { trigger: '.showcase', start: 'top bottom', end: 'bottom top', scrub: true } });
      $$('.coach-img > img').forEach(img => gsap.fromTo(img, { y: 50 }, { y: -10, ease: 'none', scrollTrigger: { trigger: img, start: 'top bottom', end: 'bottom top', scrub: true } }));
      gsap.fromTo('.cam-frame', { y: 60, rotate: 3 }, { y: -30, rotate: -2, ease: 'none', scrollTrigger: { trigger: '.camera', start: 'top bottom', end: 'bottom top', scrub: true } });
      gsap.fromTo('.lock', { y: 70 }, { y: -30, ease: 'none', scrollTrigger: { trigger: '.live-sec', start: 'top bottom', end: 'bottom top', scrub: true } });
      $$('.notif').forEach((n, i) => gsap.from(n, { x: 80, opacity: 0, duration: .8, delay: i * .15, ease: 'power3.out', scrollTrigger: { trigger: '.lock', start: 'top 75%' } }));
      $$('.pip-row figure img').forEach((f, i) => gsap.fromTo(f, { y: 14 + (i % 2) * 14 }, { y: -14 - (i % 2) * 10, ease: 'none', scrollTrigger: { trigger: '.pip-row', start: 'top bottom', end: 'bottom top', scrub: true } }));
    }
    $$('[data-count]').forEach(el => {
      const end = +el.dataset.count; let started = false;
      new IntersectionObserver(([e], o) => {
        if (!e.isIntersecting || started) return; started = true; o.disconnect();
        const t0 = performance.now();
        const step = t => { const k = Math.min(1, (t - t0) / 1400); el.textContent = Math.round(end * (1 - Math.pow(1 - k, 3))).toLocaleString(); if (k < 1) requestAnimationFrame(step); };
        if (!reduce) requestAnimationFrame(step);
      }).observe(el);
    });
    initOrb();
  });

  // ---------------- three.js hero: an iridescent, breathing glass blob with orbiting particles
  function initOrb() {
    const canvas = $('#orb');
    if (!window.THREE || !canvas) return;
    let renderer;
    try { renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: 'low-power' }); } catch (e) { return; }
    renderer.setPixelRatio(Math.min(devicePixelRatio, 1.75));
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(40, 1, .1, 100);
    camera.position.set(0, 0, 7.5);

    const geo = new THREE.IcosahedronGeometry(1.55, 48);
    const mat = new THREE.ShaderMaterial({
      transparent: true,
      uniforms: { t: { value: 0 }, c1: { value: new THREE.Color('#7cf2c4') }, c2: { value: new THREE.Color('#9b7bff') }, c3: { value: new THREE.Color('#ff6fb5') } },
      vertexShader: `
        uniform float t; varying vec3 vN; varying vec3 vP; varying float vD;
        vec3 mod289(vec3 x){return x-floor(x*(1./289.))*289.;} vec4 mod289(vec4 x){return x-floor(x*(1./289.))*289.;}
        vec4 perm(vec4 x){return mod289(((x*34.)+1.)*x);} vec4 tis(vec4 r){return 1.79284291400159-0.85373472095314*r;}
        float snoise(vec3 v){const vec2 C=vec2(1./6.,1./3.);const vec4 D=vec4(0.,.5,1.,2.);vec3 i=floor(v+dot(v,C.yyy));vec3 x0=v-i+dot(i,C.xxx);
          vec3 g=step(x0.yzx,x0.xyz);vec3 l=1.-g;vec3 i1=min(g.xyz,l.zxy);vec3 i2=max(g.xyz,l.zxy);vec3 x1=x0-i1+C.xxx;vec3 x2=x0-i2+C.yyy;vec3 x3=x0-D.yyy;
          i=mod289(i);vec4 p=perm(perm(perm(i.z+vec4(0.,i1.z,i2.z,1.))+i.y+vec4(0.,i1.y,i2.y,1.))+i.x+vec4(0.,i1.x,i2.x,1.));
          float n_=.142857142857;vec3 ns=n_*D.wyz-D.xzx;vec4 j=p-49.*floor(p*ns.z*ns.z);vec4 x_=floor(j*ns.z);vec4 y_=floor(j-7.*x_);
          vec4 x=x_*ns.x+ns.yyyy;vec4 y=y_*ns.x+ns.yyyy;vec4 h=1.-abs(x)-abs(y);vec4 b0=vec4(x.xy,y.xy);vec4 b1=vec4(x.zw,y.zw);
          vec4 s0=floor(b0)*2.+1.;vec4 s1=floor(b1)*2.+1.;vec4 sh=-step(h,vec4(0.));vec4 a0=b0.xzyw+s0.xzyw*sh.xxyy;vec4 a1=b1.xzyw+s1.xzyw*sh.zzww;
          vec3 p0=vec3(a0.xy,h.x);vec3 p1=vec3(a0.zw,h.y);vec3 p2=vec3(a1.xy,h.z);vec3 p3=vec3(a1.zw,h.w);vec4 norm=tis(vec4(dot(p0,p0),dot(p1,p1),dot(p2,p2),dot(p3,p3)));
          p0*=norm.x;p1*=norm.y;p2*=norm.z;p3*=norm.w;vec4 m=max(.6-vec4(dot(x0,x0),dot(x1,x1),dot(x2,x2),dot(x3,x3)),0.);m=m*m;
          return 42.*dot(m*m,vec4(dot(p0,x0),dot(p1,x1),dot(p2,x2),dot(p3,x3)));}
        void main(){ float d = snoise(normal*1.2 + vec3(t*.18)) * .32 + snoise(normal*3.1 - vec3(t*.25)) * .07; vD = d;
          vec3 p = position + normal * d; vN = normalize(normalMatrix * normal); vec4 mv = modelViewMatrix * vec4(p,1.); vP = mv.xyz; gl_Position = projectionMatrix * mv; }`,
      fragmentShader: `
        uniform float t; uniform vec3 c1; uniform vec3 c2; uniform vec3 c3; varying vec3 vN; varying vec3 vP; varying float vD;
        void main(){ vec3 v = normalize(-vP); float fr = pow(1. - max(dot(vN, v), 0.), 2.2);
          float k = .5 + .5 * sin(vD * 9. + t * .6 + vN.y * 3.);
          vec3 col = mix(c1, c2, k); col = mix(col, c3, smoothstep(.55, 1., fr) * .55);
          float spec = pow(max(dot(reflect(-normalize(vec3(.6,.8,.5)), vN), v), 0.), 24.);
          gl_FragColor = vec4(col * (.25 + fr * 1.1) + spec * .8, .32 + fr * .6); }`,
    });
    const blob = new THREE.Mesh(geo, mat);
    scene.add(blob);
    // orbiting particles
    const N = 420, pos = new Float32Array(N * 3);
    for (let i = 0; i < N; i++) { const r = 2.4 + Math.random() * 1.8, a = Math.random() * Math.PI * 2, y = (Math.random() - .5) * 1.6; pos.set([Math.cos(a) * r, y, Math.sin(a) * r], i * 3); }
    const pg = new THREE.BufferGeometry(); pg.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    const pts = new THREE.Points(pg, new THREE.PointsMaterial({ color: 0xbff7e6, size: .03, transparent: true, opacity: .7 }));
    pts.rotation.x = .35; scene.add(pts);
    // thin ring
    const ring = new THREE.Mesh(new THREE.TorusGeometry(2.6, .008, 8, 160), new THREE.MeshBasicMaterial({ color: 0x7cf2c4, transparent: true, opacity: .35 }));
    ring.rotation.x = 1.2; scene.add(ring);

    function size() {
      const w = canvas.clientWidth, h = canvas.clientHeight; renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix();
      const wide = w > 900; blob.position.set(wide ? 2.2 : 0, wide ? 0 : -1.7, 0); pts.position.copy(blob.position); ring.position.copy(blob.position);
      blob.scale.setScalar(wide ? 1 : .72);
    }
    addEventListener('resize', size); size();
    let visible = true;
    new IntersectionObserver(([e]) => { visible = e.isIntersecting; }).observe(canvas);
    const t0 = performance.now();
    (function loop() {
      requestAnimationFrame(loop);
      if (!visible) return;
      const t = (performance.now() - t0) / 1000;
      mat.uniforms.t.value = reduce ? 0 : t;
      const nx = (tx / innerWidth - .5), ny = (ty / innerHeight - .5);
      blob.rotation.y = t * .12 + nx * .6; blob.rotation.x = ny * .4;
      pts.rotation.y = -t * .05; ring.rotation.z = t * .08;
      camera.position.y = -scrollY * .0025;
      renderer.render(scene, camera);
    })();
  }
})();
