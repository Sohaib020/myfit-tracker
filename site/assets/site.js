(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  $('#yr').textContent = new Date().getFullYear();

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

  // ---------------- reveal on scroll
  const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }), { threshold: .12, rootMargin: '0px 0px -40px 0px' });
  $$('.reveal').forEach((el, i) => { el.style.transitionDelay = (el.closest('.pip-row,.cast-grid,.bento,.faq-list,.coach-grid') ? (i % 7) * 70 : 0) + 'ms'; io.observe(el); });

  // ---------------- nav
  const nav = $('#nav');
  const onScroll = () => nav.classList.toggle('solid', scrollY > 40);
  addEventListener('scroll', onScroll, { passive: true }); onScroll();

  // ---------------- cursor glow + hero parallax (mouse)
  const glow = $('.cursor-glow');
  let mx = innerWidth / 2, my = innerHeight / 2, tx = mx, ty = my;
  addEventListener('pointermove', e => { mx = e.clientX; my = e.clientY; }, { passive: true });
  const layers = $$('#heroArt [data-depth]');
  const speedEls = $$('[data-speed]');
  function frame() {
    tx += (mx - tx) * .12; ty += (my - ty) * .12;
    if (glow) glow.style.transform = `translate(${tx - 260}px, ${ty - 260}px)`;
    const nx = (tx / innerWidth - .5), ny = (ty / innerHeight - .5);
    const sy = scrollY;
    layers.forEach(l => { const d = +l.dataset.depth; l.style.translate = `${-nx * 40 * d}px ${-ny * 30 * d - sy * .12 * d}px`; });
    speedEls.forEach(l => { l.style.transform = `translateY(${sy * +l.dataset.speed}px)`; });
    requestAnimationFrame(frame);
  }
  if (!reduce) requestAnimationFrame(frame);

  // ---------------- 3D tilt cards
  $$('.tilt').forEach(c => {
    c.addEventListener('pointermove', e => {
      if (reduce || e.pointerType === 'touch') return;
      const r = c.getBoundingClientRect(); const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
      c.style.transform = `perspective(900px) rotateY(${x * 10}deg) rotateX(${-y * 10}deg) translateZ(0)`;
    });
    c.addEventListener('pointerleave', () => { c.style.transform = ''; });
  });
  grid.addEventListener('pointermove', e => {
    const c = e.target.closest('.tilt'); if (!c || reduce || e.pointerType === 'touch') return;
    const r = c.getBoundingClientRect(); const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
    c.style.transform = `perspective(700px) rotateY(${x * 14}deg) rotateX(${-y * 14}deg)`;
  });
  grid.addEventListener('pointerout', e => { const c = e.target.closest('.tilt'); if (c) c.style.transform = ''; });

  // ---------------- rep counter in the trainer mockup
  const rc = $('.repcount'), word = $('.s-train .counter em'), deg = $('.s-train .deg');
  if (rc && !reduce) {
    let n = 6, down = true;
    setInterval(() => {
      down = !down;
      word.textContent = down ? 'DOWN' : 'UP';
      deg.textContent = (down ? 92 : 168) + '°';
      if (!down) { n = n >= 12 ? 1 : n + 1; rc.textContent = n; }
    }, 1100);
  }

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

  // ---------------- GSAP: pinned horizontal tour, count-ups
  addEventListener('load', () => {
    if (window.gsap && window.ScrollTrigger && !reduce) {
      gsap.registerPlugin(ScrollTrigger);
      const mm = gsap.matchMedia();
      mm.add('(min-width: 901px)', () => {
        const track = $('.tour-track');
        const dist = () => track.scrollWidth - innerWidth;
        const tw = gsap.to(track, { x: () => -dist(), ease: 'none', scrollTrigger: { trigger: '.tour-pin', start: 'top 12%', end: () => '+=' + dist(), pin: true, scrub: 0.6, invalidateOnRefresh: true, anticipatePin: 1 } });
        $$('.panel').forEach(p => {
          gsap.from(p.querySelector('.phone'), { y: 80, rotate: 6, opacity: .2, ease: 'power2.out', scrollTrigger: { trigger: p, containerAnimation: tw, start: 'left 90%', end: 'left 40%', scrub: true } });
          gsap.from(p.querySelector('.panel-copy'), { x: 60, opacity: 0, ease: 'power2.out', scrollTrigger: { trigger: p, containerAnimation: tw, start: 'left 85%', end: 'left 45%', scrub: true } });
        });
      });
      mm.add('(max-width: 900px)', () => {
        $$('.panel').forEach(p => gsap.from(p, { y: 60, opacity: 0, duration: .9, ease: 'power3.out', scrollTrigger: { trigger: p, start: 'top 85%' } }));
      });
      gsap.to('.hero-copy', { y: -80, opacity: .2, ease: 'none', scrollTrigger: { trigger: '.hero', start: 'top top', end: 'bottom top', scrub: true } });
      $$('.coach-img img').forEach(img => gsap.fromTo(img, { y: 50, scale: .94 }, { y: -10, scale: 1, ease: 'none', scrollTrigger: { trigger: img, start: 'top bottom', end: 'bottom top', scrub: true } }));
      $$('.pip-row figure img').forEach((f, i) => gsap.fromTo(f, { y: 14 + (i % 2) * 14 }, { y: -14 - (i % 2) * 10, ease: 'none', scrollTrigger: { trigger: '.pip-row', start: 'top bottom', end: 'bottom top', scrub: true } }));
    }
    // count-ups (marquee numbers)
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
