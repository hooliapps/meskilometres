import Foundation
import KilometresCore

/// Formats dans la langue de l'appareil.
enum Fmt {
	static func km(_ v: Double) -> String {
		let f = NumberFormatter()
		f.numberStyle = .decimal
		f.maximumFractionDigits = 1
		return f.string(from: NSNumber(value: v)) ?? "\(v)"
	}

	static func money(_ v: Double, _ currency: String) -> String {
		let f = NumberFormatter()
		f.numberStyle = .currency
		f.currencyCode = currency
		return f.string(from: NSNumber(value: v)) ?? "\(v)"
	}

	static func date(_ d: Day) -> Date {
		var c = DateComponents()
		c.year = Int(d.year); c.month = Int(d.month); c.day = Int(d.day)
		return Calendar.current.date(from: c) ?? Date()
	}

	static func day(_ d: Day) -> String {
		let f = DateFormatter()
		f.dateStyle = .medium
		return f.string(from: date(d))
	}

	static func dayLong(_ d: Day) -> String {
		let today = Day.companion.today()
		if d == today { return tr("today") }
		if d == today.plusDays(n: -1) { return tr("yesterday") }
		let f = DateFormatter()
		f.dateStyle = .full
		return f.string(from: date(d))
	}

	static func day(from date: Date) -> Day {
		let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
		return Day(year: Int32(c.year ?? 1970), month: Int32(c.month ?? 1), day: Int32(c.day ?? 1))
	}

	static func number(_ v: Double) -> String { v == v.rounded() ? String(Int64(v)) : String(v) }

	static func parse(_ s: String) -> Double? { Double(s.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".").replacingOccurrences(of: " ", with: "")) }

	static func purpose(_ p: Purpose) -> String {
		switch p {
		case .commute: return tr("purpose_commute")
		case .business: return tr("purpose_business")
		default: return tr("purpose_personal")
		}
	}

	static func kind(_ k: ExpenseKind) -> String {
		switch k {
		case .toll: return tr("kind_toll")
		case .parking: return tr("kind_parking")
		case .fuel: return tr("kind_fuel")
		default: return tr("kind_other")
		}
	}

	static func vehicleKind(_ k: VehicleKind) -> String {
		switch k {
		case .car: return tr("kind_car")
		case .motorcycle: return tr("kind_motorcycle")
		default: return tr("kind_moped")
		}
	}

	static let purposes: [Purpose] = [.commute, .business, .personal]
	static let expenseKinds: [ExpenseKind] = [.toll, .parking, .fuel, .other]
	static let vehicleKinds: [VehicleKind] = [.car, .motorcycle, .moped]
}
