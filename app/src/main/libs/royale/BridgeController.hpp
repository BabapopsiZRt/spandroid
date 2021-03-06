/****************************************************************************\
 * Copyright (C) 2016 Infineon Technologies
 *
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY
 * KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
 * PARTICULAR PURPOSE.
 *
 \****************************************************************************/

#pragma once

#include <factory/ICameraCoreBuilder.hpp>
#include <usb/enumerator/IBusEnumerator.hpp>
#include <usb/config/UsbProbeData.hpp>

#include <vector>
#include <memory>

namespace royale
{
    namespace factory
    {
        /**
         * The BridgeController can probe USB-based devices, but can also generate
         * the correct bridge interfaces. After creation, the bridge is already connected.
         */
        class BridgeController
        {
        public:
            /**
            * Construct a BridgeController which probes all camera modules officially supported by Royale.
            */
            BridgeController();

            /**
            * Construct a BridgeController with a list of camera modules to probe.
            */
            explicit BridgeController (const royale::usb::config::UsbProbeDataList &probeData);

            /**
            * This probes for all attached USB-based cameras and delivers a list of
            * ICameraCoreBuilder objects.
            * In the ICameraCoreBuilder instances returned, the config and bridge factory
            * will already be set to the appropriate values.
            *
            * If needed, the user should call setAccessLevel() before creating the
            * camera core with createCameraCore().
            *
            * \return A list of ICameraCoreBuilder instances, ready to use.
            */
#if defined(TARGET_PLATFORM_ANDROID)
            std::vector<std::unique_ptr<royale::factory::ICameraCoreBuilder>> probeDevices (uint32_t androidUsbDeviceFD,
                    uint32_t androidUsbDeviceVid,
                    uint32_t androidUsbDevicePid);
#else
            std::vector<std::unique_ptr<royale::factory::ICameraCoreBuilder>> probeDevices();
#endif
        private:
            royale::usb::config::UsbProbeDataList m_probeData;
        };
    }
}
