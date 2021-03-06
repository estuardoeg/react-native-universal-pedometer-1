'use strict';

import {NativeModules, NativeEventEmitter} from 'react-native';
const {BMDPedometer} = NativeModules;

const EventEmitter = new NativeEventEmitter(BMDPedometer);
let subscription;

export default {
    isStepCountingAvailable: callback => {
        BMDPedometer.isStepCountingAvailable(callback);
    },

    isDistanceAvailable: callback => {
        BMDPedometer.isDistanceAvailable(callback);
    },

    isFloorCountingAvailable: callback => {
        BMDPedometer.isFloorCountingAvailable(callback);
    },

    isCadenceAvailable: callback => {
        BMDPedometer.isCadenceAvailable(callback);
    },

    startPedometerUpdatesFromDate: (date, handler) => {
        BMDPedometer.startPedometerUpdatesFromDate(date);

        subscription = EventEmitter.addListener('pedometerDataDidUpdate', handler);
    },

    queryPedometerDataBetweenDates: (startDate, endDate, handler) => {
        BMDPedometer.queryPedometerDataBetweenDates(startDate, endDate, handler);
    },

    stopPedometerUpdates: () => {
        BMDPedometer.stopPedometerUpdates();

        if (subscription) {
            subscription.remove();
        }
    }
};
