import { Observable } from 'rxjs/Observable';
import { ReservationView } from '../../viewModels/interfaces';
import { BookingInfo, OrderListInfo } from '../backendModels/interfaces';

export interface IBookingDataService {

    getBookingId(): Observable<number>;
    getOrders(): Observable<ReservationView[]>;
    getOrder(id: number): Observable<ReservationView>;
    getReservations(): Observable<ReservationView[]>;
    getReservation(id: number): Observable<ReservationView>;
    bookTable(booking: BookingInfo): Observable<number>;
    saveOrders(orders: OrderListInfo): Observable<ReservationView>;

}
