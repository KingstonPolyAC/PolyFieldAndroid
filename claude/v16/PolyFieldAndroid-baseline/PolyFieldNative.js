import { NativeModules } from 'react-native';

const { PolyField } = NativeModules;

/**
 * PolyField Native Module Interface
 * Provides access to the Go Mobile calculation engine with exact precision
 */
class PolyFieldNative {

    /**
     * Initialize the native module (called automatically)
     */
    static async initialize() {
        // Module is auto-initialized, but we can call this explicitly if needed
        return Promise.resolve(true);
    }

    /**
     * Set demo mode on/off
     * @param {boolean} enabled - Whether to enable demo mode
     */
    static async setDemoMode(enabled) {
        try {
            return await PolyField.setDemoMode(enabled);
        } catch (error) {
            console.error('Error setting demo mode:', error);
            throw error;
        }
    }

    /**
     * Get current demo mode status
     */
    static async getDemoMode() {
        try {
            return await PolyField.getDemoMode();
        } catch (error) {
            console.error('Error getting demo mode:', error);
            return true; // Default to demo mode
        }
    }

    /**
     * Get calibration data for a device
     * @param {string} deviceType - Device type ('edm', 'wind', etc.)
     */
    static async getCalibration(deviceType = 'edm') {
        try {
            return await PolyField.getCalibration(deviceType);
        } catch (error) {
            console.error('Error getting calibration:', error);
            throw error;
        }
    }

    /**
     * Save calibration data for a device
     * @param {string} deviceType - Device type
     * @param {object} calibrationData - Calibration data object
     */
    static async saveCalibration(deviceType, calibrationData) {
        try {
            return await PolyField.saveCalibration(deviceType, calibrationData);
        } catch (error) {
            console.error('Error saving calibration:', error);
            throw error;
        }
    }

    /**
     * Set circle centre using exact EDM calculations
     * @param {string} deviceType - Device type
     */
    static async setCircleCentre(deviceType = 'edm') {
        try {
            const result = await PolyField.setCircleCentre(deviceType);
            console.log('Circle centre set:', result);
            return result;
        } catch (error) {
            console.error('Error setting circle centre:', error);
            throw error;
        }
    }

    /**
     * Verify circle edge with UKA tolerance standards
     * @param {string} deviceType - Device type
     */
    static async verifyCircleEdge(deviceType = 'edm') {
        try {
            const result = await PolyField.verifyCircleEdge(deviceType);
            console.log('Circle edge verified:', result);
            return result;
        } catch (error) {
            console.error('Error verifying circle edge:', error);
            throw error;
        }
    }

    /**
     * Measure throw distance using exact trigonometric calculations
     * @param {string} deviceType - Device type
     */
    static async measureThrow(deviceType = 'edm') {
        try {
            const result = await PolyField.measureThrow(deviceType);
            console.log('Throw measured:', result);
            return result;
        } catch (error) {
            console.error('Error measuring throw:', error);
            throw error;
        }
    }

    /**
     * Measure wind speed
     */
    static async measureWind() {
        try {
            const result = await PolyField.measureWind();
            console.log('Wind measured:', result);
            return result;
        } catch (error) {
            console.error('Error measuring wind:', error);
            throw error;
        }
    }

    /**
     * Get all stored throw coordinates
     */
    static async getThrowCoordinates() {
        try {
            const result = await PolyField.getThrowCoordinates();
            return JSON.parse(result.coordinates);
        } catch (error) {
            console.error('Error getting throw coordinates:', error);
            return [];
        }
    }

    /**
     * Get statistics for throws of a specific circle type
     * @param {string} circleType - Circle type ('SHOT', 'DISCUS', 'HAMMER', 'JAVELIN_ARC')
     */
    static async getThrowStatistics(circleType) {
        try {
            const result = await PolyField.getThrowStatistics(circleType);
            if (result.error) {
                return null;
            }
            return result;
        } catch (error) {
            console.error('Error getting throw statistics:', error);
            return null;
        }
    }

    /**
     * Clear all stored throw coordinates
     */
    static async clearThrowCoordinates() {
        try {
            return await PolyField.clearThrowCoordinates();
        } catch (error) {
            console.error('Error clearing throw coordinates:', error);
            throw error;
        }
    }

    /**
     * Get UKA official radius for a circle type
     * @param {string} circleType - Circle type
     */
    static async getUKARadius(circleType) {
        try {
            return await PolyField.getUKARadius(circleType);
        } catch (error) {
            console.error('Error getting UKA radius:', error);
            throw error;
        }
    }

    /**
     * Get tolerance for a circle type (in mm)
     * @param {string} circleType - Circle type
     */
    static async getTolerance(circleType) {
        try {
            return await PolyField.getTolerance(circleType);
        } catch (error) {
            console.error('Error getting tolerance:', error);
            throw error;
        }
    }

    /**
     * Reset calibration for a device
     * @param {string} deviceType - Device type
     */
    static async resetCalibration(deviceType = 'edm') {
        try {
            return await PolyField.resetCalibration(deviceType);
        } catch (error) {
            console.error('Error resetting calibration:', error);
            throw error;
        }
    }

    /**
     * Export throw coordinates as CSV string
     */
    static async exportThrowCoordinatesCSV() {
        try {
            const coordinates = await this.getThrowCoordinates();

            if (coordinates.length === 0) {
                return '';
            }

            // Create CSV header
            let csv = 'X,Y,Distance,CircleType,Timestamp,AthleteID,CompetitionRound,EDMReading\n';

            // Add data rows
            coordinates.forEach(coord => {
                const timestamp = new Date(coord.timestamp * 1000).toISOString();
                csv += `${coord.x.toFixed(6)},${coord.y.toFixed(6)},${coord.distance.toFixed(3)},`;
                csv += `${coord.circleType},${timestamp},${coord.athleteId || ''},`;
                csv += `${coord.competitionRound || ''},"${coord.edmReading || ''}"\n`;
            });

            return csv;
        } catch (error) {
            console.error('Error exporting CSV:', error);
            throw error;
        }
    }

    /**
     * Generate heat map data for visualization
     * @param {string} circleType - Circle type to filter by
     * @param {number} gridSize - Grid size in meters (default 0.5)
     */
    static async generateHeatMapData(circleType, gridSize = 0.5) {
        try {
            const coordinates = await this.getThrowCoordinates();
            const filteredCoords = coordinates.filter(coord => coord.circleType === circleType);

            if (filteredCoords.length === 0) {
                const radius = await this.getUKARadius(circleType);
                return {
                    coordinates: [],
                    bounds: {
                        minX: -radius * 2,
                        maxX: radius * 2,
                        minY: -radius * 2,
                        maxY: 50 // Default throw area
                    },
                    stats: {
                        totalThrows: 0,
                        averageDistance: 0,
                        maxDistance: 0,
                        minDistance: 0
                    },
                    circleType,
                    targetRadius: radius,
                    gridSize
                };
            }

            // Calculate bounds
            const xCoords = filteredCoords.map(c => c.x);
            const yCoords = filteredCoords.map(c => c.y);
            const distances = filteredCoords.map(c => c.distance);

            const bounds = {
                minX: Math.min(...xCoords),
                maxX: Math.max(...xCoords),
                minY: Math.min(...yCoords),
                maxY: Math.max(...yCoords)
            };

            // Calculate statistics
            const stats = {
                totalThrows: filteredCoords.length,
                averageDistance: distances.reduce((sum, d) => sum + d, 0) / distances.length,
                maxDistance: Math.max(...distances),
                minDistance: Math.min(...distances)
            };

            const radius = await this.getUKARadius(circleType);

            return {
                coordinates: filteredCoords,
                bounds,
                stats,
                circleType,
                targetRadius: radius,
                gridSize
            };
        } catch (error) {
            console.error('Error generating heat map data:', error);
            throw error;
        }
    }

    /**
     * Helper function to update circle type in calibration
     * @param {string} circleType - New circle type
     */
    static async setCircleType(circleType) {
        try {
            const currentCal = await this.getCalibration('edm');
            const radius = await this.getUKARadius(circleType);

            const updatedCal = {
                ...currentCal,
                selectedCircleType: circleType,
                targetRadius: radius,
                isCentreSet: false,
                edgeVerificationResult: null,
                timestamp: Math.floor(Date.now() / 1000)
            };

            await this.saveCalibration('edm', updatedCal);
            return updatedCal;
        } catch (error) {
            console.error('Error setting circle type:', error);
            throw error;
        }
    }
}

export default PolyFieldNative;