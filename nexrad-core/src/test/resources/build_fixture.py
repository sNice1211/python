#!/usr/bin/env python3
"""Builds a byte-accurate synthetic NEXRAD Archive II file for decoder unit tests.

Only exercises the fields Level2Decoder.kt actually reads (per ICD 2620002 Message
Type 31), plus one no-op VOL block to prove block dispatch skips unknown-content
blocks safely. Regenerate with: python3 build_fixture.py
"""
import bz2
import struct

STID = b"KTLX"


def volume_header():
    return b"AR2V0006." + b"001" + struct.pack(">II", 20000, 43200000) + STID


def msg31_body(az_angle, rad_status, el_angle, el_num):
    # --- moment block payloads (28-byte generic header + packed gate bytes) ---
    def moment_block(name, num_gates, first_gate, gate_width, scale, offset, raw_gates):
        header = (
            b"D" + name.encode("ascii")
            + struct.pack(">I", 0)  # reserved
            + struct.pack(">H", num_gates)
            + struct.pack(">H", first_gate)
            + struct.pack(">H", gate_width)
            + struct.pack(">H", 0)  # tover
            + struct.pack(">h", 0)  # snr threshold
            + struct.pack(">B", 0)  # recombined flags
            + struct.pack(">B", 8)  # data size bits
            + struct.pack(">f", scale)
            + struct.pack(">f", offset)
        )
        return header + bytes(raw_gates)

    ref_block = moment_block("REF", 4, 2125, 250, 2.0, 66.0, [0, 1, 100, 132])
    vel_block = moment_block("VEL", 4, 2125, 250, 2.0, 129.0, [0, 1, 127, 131])
    rho_block = moment_block("RHO", 4, 2125, 250, 200.0, -100.0, [0, 1, 100, 200])

    rad_block = (
        b"RRAD"
        + struct.pack(">H", 20)  # block size, informational
        + struct.pack(">H", 1200)  # unambiguous range raw (*0.1 -> 120.0 km)
        + struct.pack(">f", 0.0)  # noise_h
        + struct.pack(">f", 0.0)  # noise_v
        + struct.pack(">H", 3000)  # nyquist raw (*0.01 -> 30.0 m/s)
        + struct.pack(">H", 0)  # pad
    )
    vol_block = b"RVOL"  # decoder only reads the 4-byte tag then moves on

    data_header_len = 32
    num_blocks = 5
    pointer_table_len = num_blocks * 4
    blocks_start = data_header_len + pointer_table_len

    offsets = []
    pos = blocks_start
    for block in (rad_block, vol_block, ref_block, vel_block, rho_block):
        offsets.append(pos)
        pos += len(block)

    data_header = (
        STID
        + struct.pack(">I", 12345678)  # time_ms
        + struct.pack(">H", 20000)  # date (julian)
        + struct.pack(">H", 1)  # azimuth number
        + struct.pack(">f", az_angle)
        + struct.pack(">B", 0)  # compression
        + struct.pack(">B", 0)  # spare
        + struct.pack(">H", pos)  # radial length in bytes
        + struct.pack(">B", 0)  # azimuth spacing code (0 -> 0.5 deg)
        + struct.pack(">B", rad_status)
        + struct.pack(">B", el_num)
        + struct.pack(">B", 0)  # cut sector number
        + struct.pack(">f", el_angle)
        + struct.pack(">B", 0)  # spot blanking
        + struct.pack(">B", 0)  # azimuth indexing mode
        + struct.pack(">H", num_blocks)
    )
    pointer_table = b"".join(struct.pack(">i", off) for off in offsets)
    body = data_header + pointer_table + rad_block + vol_block + ref_block + vel_block + rho_block
    assert len(body) == pos, (len(body), pos)
    return body


def wrap_message(msg_type, body):
    size_hw = (16 + len(body)) // 2
    header = struct.pack(
        ">HBBHHIHH",
        size_hw,
        0,  # rda channel bitfield
        msg_type,
        1,  # sequence number
        20000,  # date
        43200000,  # ms of day
        1,  # num segments
        1,  # segment number
    )
    ctm = b"\x00" * 12
    return ctm + header + body


def build():
    radial1 = wrap_message(31, msg31_body(az_angle=0.5, rad_status=3, el_angle=0.5, el_num=1))
    radial2 = wrap_message(31, msg31_body(az_angle=1.5, rad_status=4, el_angle=0.5, el_num=1))
    message_stream = radial1 + radial2

    compressed = bz2.compress(message_stream)
    ldm_record = struct.pack(">i", len(compressed)) + compressed

    return volume_header() + ldm_record


if __name__ == "__main__":
    data = build()
    with open("level2_sample.ar2", "wb") as f:
        f.write(data)
    print(f"wrote {len(data)} bytes")
