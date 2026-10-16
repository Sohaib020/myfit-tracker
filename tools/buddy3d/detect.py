import json, glob, os, sys
import cv2, mediapipe as mp
d = '/tmp/claude-0/bud/front'
meta = json.load(open(f'{d}/meta.json'))
P = mp.solutions.pose
L = P.PoseLandmark
want = {'nose': L.NOSE, 'sh_l': L.LEFT_SHOULDER, 'sh_r': L.RIGHT_SHOULDER, 'el_l': L.LEFT_ELBOW, 'el_r': L.RIGHT_ELBOW,
        'wr_l': L.LEFT_WRIST, 'wr_r': L.RIGHT_WRIST, 'hip_l': L.LEFT_HIP, 'hip_r': L.RIGHT_HIP, 'kn_l': L.LEFT_KNEE, 'kn_r': L.RIGHT_KNEE,
        'an_l': L.LEFT_ANKLE, 'an_r': L.RIGHT_ANKLE, 'idx_l': L.LEFT_INDEX, 'idx_r': L.RIGHT_INDEX, 'heel_l': L.LEFT_HEEL, 'heel_r': L.RIGHT_HEEL,
        'toe_l': L.LEFT_FOOT_INDEX, 'toe_r': L.RIGHT_FOOT_INDEX}
res = {}
with P.Pose(static_image_mode=True, model_complexity=1, min_detection_confidence=0.3) as pose:
    for name, m in meta.items():
        img = cv2.imread(f'{d}/{name}.png'); h, w = img.shape[:2]
        r = pose.process(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
        if not r.pose_landmarks: print('NONE', name); continue
        lm = r.pose_landmarks.landmark
        pts = {}
        for k, i in want.items():
            u, v = lm[i].x, lm[i].y
            # pixel -> world (x, z): character faces -Y, camera looks +Y from the front; image left = world -x? (camera at -Y looking +Y: image right = +x)
            x = m['cx'] + (u - 0.5) * m['scale'] * w / h
            z = m['cz'] - (v - 0.5) * m['scale']
            pts[k] = (x, z, lm[i].visibility)
        res[name] = pts
        vis = min(p[2] for p in pts.values())
        print('OK', name, 'minvis=%.2f' % vis)
        for k in ('sh_l','el_l','wr_l','hip_l','kn_l','an_l','sh_r','el_r','wr_r','hip_r','kn_r','an_r'):
            cv2.circle(img, (int(lm[want[k]].x*w), int(lm[want[k]].y*h)), 7, (0,0,255) if k.endswith('_l') else (255,0,0), -1)
        cv2.imwrite(f'/tmp/claude-0/bud/det/{name}.jpg', img[:, :, :])
json.dump(res, open('/tmp/claude-0/bud/det.json', 'w'), indent=1)
