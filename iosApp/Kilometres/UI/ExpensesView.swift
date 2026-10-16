import KilometresCore
import SwiftUI

struct ExpensesView: View {
	@EnvironmentObject private var model: AppModel
	@State private var editing: Expense?
	@State private var adding = false

	var body: some View {
		Group {
			if !model.allows(.expenses) {
				ScrollView { ProLockedCard().padding() }
			} else {
				List {
					let expenses = model.data.expenses.sorted { $0.date.compareTo(other: $1.date) > 0 }
					if expenses.isEmpty { Text(tr("no_expenses")).foregroundStyle(.secondary) }
					ForEach(expenses, id: \.id) { e in
						Button { editing = e } label: {
							HStack {
								Image(systemName: e.kind == .toll ? "road.lanes" : e.kind == .parking ? "parkingsign" : e.kind == .fuel ? "fuelpump" : "doc")
								VStack(alignment: .leading) {
									Text([Fmt.kind(e.kind), e.place].filter { !$0.isEmpty }.joined(separator: " · "))
									Text(Fmt.day(e.date)).font(.caption).foregroundStyle(.secondary)
								}
								Spacer()
								Text(Fmt.money(e.amount, e.currency.isEmpty ? model.data.settings.currency : e.currency)).font(.headline)
							}
						}.buttonStyle(.plain)
					}
				}
				.toolbar { ToolbarItem(placement: .primaryAction) { Button { adding = true } label: { Label(tr("add_expense"), systemImage: "plus") } } }
			}
		}
		.navigationTitle(tr("expenses"))
		.sheet(isPresented: $adding) { NavigationStack { ExpenseFormView(expenseId: nil) } }
		.sheet(item: $editing) { e in NavigationStack { ExpenseFormView(expenseId: e.id) } }
	}
}

extension Expense: Identifiable {}

struct ExpenseFormView: View {
	let expenseId: String?
	@EnvironmentObject private var model: AppModel
	@Environment(\.dismiss) private var dismiss
	@State private var date = Date()
	@State private var kind: ExpenseKind = .toll
	@State private var amount = ""
	@State private var place = ""
	@State private var litres = ""
	@State private var odometer = ""
	@State private var error: String?
	@State private var loaded = false

	private var existing: Expense? { expenseId.flatMap { id in model.data.expenses.first { $0.id == id } } }

	var body: some View {
		Form {
			DatePicker(tr("field_date"), selection: $date, displayedComponents: .date)
			Picker(tr("expense_kind"), selection: $kind) { ForEach(Fmt.expenseKinds, id: \.self) { Text(Fmt.kind($0)).tag($0) } }.pickerStyle(.segmented)
			HStack { TextField(tr("amount"), text: $amount).keyboardType(.decimalPad); Text(model.data.settings.currency).foregroundStyle(.secondary) }
			if let error { Text(error).font(.caption).foregroundStyle(.red) }
			TextField(tr("place"), text: $place)
			if kind == .fuel {
				TextField(tr("litres"), text: $litres).keyboardType(.decimalPad)
				HStack { TextField(tr("odometer"), text: $odometer).keyboardType(.numberPad); Text("km").foregroundStyle(.secondary) }
			}
			Section {
				Button(tr("save")) {
					guard let a = Fmt.parse(amount), a > 0 else { error = tr("invalid_amount"); return }
					let e = Make.shared.expense(id: existing?.id ?? model.state.newId(), date: Fmt.day(from: date), kind: kind, amount: a, currency: model.data.settings.currency, place: place.trimmingCharacters(in: .whitespaces),
						litres: kind == .fuel ? Fmt.parse(litres).map { KotlinDouble(double: $0) } : nil, odometerKm: kind == .fuel ? Fmt.parse(odometer).map { KotlinDouble(double: $0) } : nil)
					model.state.upsertExpense(e: e)
					dismiss()
				}.frame(maxWidth: .infinity).buttonStyle(.borderedProminent)
				if let e = existing { Button(tr("delete"), role: .destructive) { model.state.deleteExpense(id: e.id); dismiss() }.frame(maxWidth: .infinity) }
			}
		}
		.navigationTitle(tr(existing == nil ? "add_expense" : "edit_expense"))
		.toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("cancel")) { dismiss() } } }
		.onAppear {
			guard !loaded else { return }
			loaded = true
			if let e = existing {
				date = Fmt.date(e.date); kind = e.kind; amount = String(format: "%.2f", e.amount); place = e.place
				litres = e.litres.map { Fmt.number($0.doubleValue) } ?? ""; odometer = e.odometerKm.map { Fmt.number($0.doubleValue) } ?? ""
			}
		}
	}
}
