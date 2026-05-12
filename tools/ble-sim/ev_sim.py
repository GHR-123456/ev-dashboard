"""
EV 控制器 BLE 模拟器 (winrt 直接调用版)
========================================

绕开 bless 直接使用 WinRT API,避免 bleak/bless 在 Python 3.14 上的兼容问题。

依赖:
    pip install winrt-runtime winrt-Windows.Devices.Bluetooth winrt-Windows.Devices.Bluetooth.GenericAttributeProfile winrt-Windows.Storage.Streams

(以上包通常在 pip install bleak 时已自动安装。)

运行:
    python ev_sim.py

广播信息:
    设备名:  Windows 蓝牙设备名 (查看「设置 → 蓝牙 → 此 PC 的名称」)
    Service: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E (Nordic UART)
    Notify:  6E400003-...        (PC → 手机)
    Write:   6E400002-...        (手机 → PC)

推送频率: 10 Hz (每 100ms 一帧, 16 字节)
"""

import asyncio
import logging
import math
import socket
import struct
import sys
import time
import uuid

from winrt.windows.devices.bluetooth.genericattributeprofile import (
    GattCharacteristicProperties,
    GattLocalCharacteristicParameters,
    GattProtectionLevel,
    GattServiceProvider,
    GattServiceProviderAdvertisementStatus,
    GattServiceProviderAdvertisingParameters,
)
from winrt.windows.storage.streams import DataReader, DataWriter

NUS_SERVICE_UUID = uuid.UUID("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
NUS_WRITE_UUID = uuid.UUID("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
NUS_NOTIFY_UUID = uuid.UUID("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

FRAME_RATE_HZ = 10
FRAME_INTERVAL = 1.0 / FRAME_RATE_HZ

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("ev-sim")


def build_frame(seq: int, t: float) -> bytes:
    """根据时间 t 生成一帧模拟数据,用正弦波模拟一段加速/减速过程"""

    phase = (t * 0.2) % (2 * math.pi)
    throttle = (math.sin(phase) + 1) / 2          # 0~1

    voltage_v = 48.0 - throttle * 4.0             # 48V → 44V
    current_a = throttle * 35.0 - 2.0             # -2A ~ +33A
    rpm = int(throttle * 4500)                    # 0 ~ 4500
    speed_kmh = int(throttle * 55)                # 0 ~ 55
    temp_ctrl = int(35 + throttle * 25)           # 35 ~ 60 ℃
    temp_motor = int(40 + throttle * 30)          # 40 ~ 70 ℃
    soc_pct = int(80 - (t * 0.05) % 50)           # 80% 缓慢下降
    gear = 1 + int(throttle * 2.99)               # 1/2/3

    voltage_x10 = int(round(voltage_v * 10))
    current_x10 = int(round(current_a * 10))

    payload = struct.pack(
        "<BBBBHhHBbbBB",
        0xAA, 0x55,
        0x01,
        seq & 0xFF,
        voltage_x10 & 0xFFFF,
        current_x10,
        rpm & 0xFFFF,
        speed_kmh & 0xFF,
        temp_ctrl,
        temp_motor,
        soc_pct & 0xFF,
        gear & 0xFF,
    )

    checksum = 0
    for b in payload:
        checksum ^= b
    return payload + bytes([checksum])


def bytes_to_buffer(data: bytes):
    writer = DataWriter()
    writer.write_bytes(data)
    return writer.detach_buffer()


def buffer_to_bytes(buf) -> bytes:
    reader = DataReader.from_buffer(buf)
    out = bytearray(buf.length)
    for i in range(buf.length):
        out[i] = reader.read_byte()
    return bytes(out)


class EVSimulator:
    def __init__(self) -> None:
        self.service_provider: GattServiceProvider | None = None
        self.notify_char = None
        self.write_char = None
        self.subscriber_count = 0

    async def setup(self) -> None:
        # 创建 service
        result = await GattServiceProvider.create_async(NUS_SERVICE_UUID)
        if result.error != 0:
            raise RuntimeError(f"create service failed, error={result.error}")
        self.service_provider = result.service_provider
        service = self.service_provider.service

        # Write 特征 (手机 → PC)
        wp = GattLocalCharacteristicParameters()
        wp.characteristic_properties = (
            GattCharacteristicProperties.WRITE
            | GattCharacteristicProperties.WRITE_WITHOUT_RESPONSE
        )
        wp.write_protection_level = GattProtectionLevel.PLAIN
        wr = await service.create_characteristic_async(NUS_WRITE_UUID, wp)
        if wr.error != 0:
            raise RuntimeError(f"create write char failed, error={wr.error}")
        self.write_char = wr.characteristic
        self.write_char.add_write_requested(self._on_write_requested)

        # Notify 特征 (PC → 手机)
        np = GattLocalCharacteristicParameters()
        np.characteristic_properties = (
            GattCharacteristicProperties.NOTIFY | GattCharacteristicProperties.READ
        )
        np.read_protection_level = GattProtectionLevel.PLAIN
        np.static_value = bytes_to_buffer(b"\x00")
        nr = await service.create_characteristic_async(NUS_NOTIFY_UUID, np)
        if nr.error != 0:
            raise RuntimeError(f"create notify char failed, error={nr.error}")
        self.notify_char = nr.characteristic
        self.notify_char.add_subscribed_clients_changed(self._on_subscribers_changed)

        # 监听广播状态
        self.service_provider.add_advertisement_status_changed(self._on_advert_status)

    def _on_write_requested(self, _sender, args):
        try:
            deferral = args.get_deferral()
            request_op = args.get_request_async()

            def handle(op, _status):
                try:
                    req = op.get_results()
                    data = buffer_to_bytes(req.value)
                    hex_str = " ".join(f"{b:02X}" for b in data)
                    logger.info(f"收到写入 ({len(data)} 字节): {hex_str}")
                    from winrt.windows.devices.bluetooth.genericattributeprofile import (
                        GattWriteOption,
                    )
                    if req.option == GattWriteOption.WRITE_WITH_RESPONSE:
                        req.respond()
                finally:
                    deferral.complete()

            request_op.completed = handle
        except Exception as e:
            logger.error(f"on_write_requested 异常: {e}")

    def _on_subscribers_changed(self, sender, _args):
        n = len(sender.subscribed_clients)
        delta = n - self.subscriber_count
        self.subscriber_count = n
        if delta > 0:
            logger.info(f"✓ 手机已订阅通知,当前订阅数: {n}")
        else:
            logger.info(f"✗ 手机取消订阅,当前订阅数: {n}")

    def _on_advert_status(self, sender, _args):
        status = sender.advertisement_status
        name = {
            GattServiceProviderAdvertisementStatus.CREATED: "已创建",
            GattServiceProviderAdvertisementStatus.STOPPED: "已停止",
            GattServiceProviderAdvertisementStatus.STARTED: "广播中",
            GattServiceProviderAdvertisementStatus.ABORTED: "已中止",
            GattServiceProviderAdvertisementStatus.STARTED_WITHOUT_ALL_ADVERTISEMENT_DATA: "广播中(部分信息未广播)",
        }.get(status, f"未知 ({status})")
        logger.info(f"广播状态: {name}")

    def start_advertising(self) -> None:
        params = GattServiceProviderAdvertisingParameters()
        params.is_connectable = True
        params.is_discoverable = True
        self.service_provider.start_advertising_with_parameters(params)

    def stop_advertising(self) -> None:
        if self.service_provider is not None:
            try:
                self.service_provider.stop_advertising()
            except Exception:
                pass

    async def notify_loop(self) -> None:
        seq = 0
        start = time.monotonic()
        next_tick = start

        while True:
            now = time.monotonic()
            frame = build_frame(seq, now - start)

            if self.subscriber_count > 0:
                try:
                    buf = bytes_to_buffer(frame)
                    await self.notify_char.notify_value_async(buf)
                except Exception as e:
                    logger.error(f"notify 失败: {e}")

            if seq % FRAME_RATE_HZ == 0:
                hex_str = " ".join(f"{b:02X}" for b in frame)
                sub = self.subscriber_count
                logger.info(f"帧 #{seq:>5} (订阅数 {sub}): {hex_str}")

            seq = (seq + 1) & 0xFFFF
            next_tick += FRAME_INTERVAL
            sleep_for = next_tick - time.monotonic()
            if sleep_for > 0:
                await asyncio.sleep(sleep_for)
            else:
                next_tick = time.monotonic()


async def main() -> None:
    sim = EVSimulator()
    await sim.setup()
    sim.start_advertising()

    hostname = socket.gethostname()
    logger.info("=" * 60)
    logger.info(f"BLE 外设已启动 (设备名以 Windows 蓝牙名称广播: {hostname})")
    logger.info(f"Service UUID: {NUS_SERVICE_UUID}")
    logger.info(f"Notify UUID:  {NUS_NOTIFY_UUID}")
    logger.info(f"Write UUID:   {NUS_WRITE_UUID}")
    logger.info(f"推送频率: {FRAME_RATE_HZ} Hz ({FRAME_INTERVAL * 1000:.0f} ms / 帧)")
    logger.info("等待手机扫描并连接... (Ctrl+C 停止)")
    logger.info("=" * 60)

    try:
        await sim.notify_loop()
    finally:
        logger.info("正在停止广播...")
        sim.stop_advertising()
        logger.info("已停止")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        sys.exit(0)
