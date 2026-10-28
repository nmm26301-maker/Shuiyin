#!/usr/bin/env python3
"""Execute generated native format expressions in the pinned, production yt-dlp ZIP."""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(sys.argv[1]).resolve()))
from yt_dlp import YoutubeDL

checks = 0
expressions = json.loads(pathlib.Path(sys.argv[2]).read_text())
heights = [360, 480, 720, 1080, 1440]
formats = [dict(format_id=str(h), height=h, width=h * 16 // 9, ext='mp4',
                vcodec='avc1', acodec='none', url=f'https://cdn.example.org/{h}') for h in heights]
audio = dict(format_id='audio', ext='m4a', vcodec='none', acodec='mp4a', url='https://cdn.example.org/audio')

def select(engine, expression, rows):
    return list(engine.build_format_selector(expression)(dict(
        formats=rows, incomplete_formats=False, has_merged_format=False)))

with YoutubeDL(dict(quiet=True, no_warnings=True)) as engine:
    for key, expression in expressions.items():
        height = int(key)
        rows = select(engine, expression, formats + [audio])
        assert len(rows) == 1 and rows[0]['height'] == (height or 1440)
        checks += 1
        assert any(row['format_id'] == 'audio' for row in rows[0]['requested_formats'])
        checks += 1
        if height:
            assert not select(engine, expression, [row for row in formats if row['height'] > height] + [audio])
            checks += 1
            complete = dict(format_id='complete', height=height, width=height*16//9,
                            ext='mp4', vcodec='avc1', acodec='mp4a', url='https://cdn.example.org/complete')
            rows = select(engine, expression, [complete])
            assert len(rows) == 1 and rows[0]['height'] == height
            checks += 1
print(f'PASS production yt-dlp format selection: {checks} checks')
